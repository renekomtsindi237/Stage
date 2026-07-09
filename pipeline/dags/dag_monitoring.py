"""
DAG : dag_monitoring
Vérifie la santé du système IMF Pipeline toutes les 5 minutes.
En cas d'incident → email à renekomtsindi7@gmail.com.
En cas de résolution → email de récupération.
Déduplication via XCom (état persisté dans /var/lib/imf-monitor/state.json sur le host).
"""

from __future__ import annotations

import json
import os
import shutil
import smtplib
import socket
import subprocess
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from pathlib import Path

from airflow import DAG
from airflow.operators.python import PythonOperator

# ── Config ─────────────────────────────────────────────────────────────────────
ALERT_TO = os.getenv("ALERT_EMAIL", "renekomtsindi7@gmail.com")
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "renekomtsindi7@gmail.com")
SMTP_PASS = os.getenv("SMTP_PASSWORD", "")
STATE_FILE = Path("/var/lib/imf-monitor/state.json")

IMF_CONTAINERS = [
    "imf-backend",
    "imf-frontend",
    "imf-airflow-scheduler",
    "imf-airflow-webserver",
    "imf_staging_redis",
]

HTTP_CHECKS = [
    ("Backend /actuator/health", "http://localhost:9200/actuator/health", 200),
    ("Frontend nginx", "http://localhost:9091/", 200),
    ("API nginx", "http://localhost:9090/actuator/health", 200),
    ("Airflow webserver", "http://localhost:8090/health", 200),
]

PG_HOST = os.getenv("POSTGRES_HOST", "aws-0-eu-west-3.pooler.supabase.com")
PG_PORT = int(os.getenv("POSTGRES_PORT", "6543"))

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 0,
    "email_on_failure": False,
    "email_on_retry": False,
}


# ── Helpers ────────────────────────────────────────────────────────────────────
def _load_state() -> dict:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text())
        except Exception:
            pass
    return {"incidents": {}}


def _save_state(state: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2))


def _send(subject: str, body_html: str, severity: str) -> None:
    emoji = {"CRITICAL": "🔴", "WARNING": "🟡", "OK": "🟢"}.get(severity, "⚪")
    msg = MIMEMultipart("alternative")
    msg["Subject"] = f"{emoji} [IMF] {subject}"
    msg["From"] = SMTP_USER
    msg["To"] = ALERT_TO

    color = {"CRITICAL": "#ef4444", "WARNING": "#f59e0b", "OK": "#22c55e"}.get(
        severity, "#94a3b8"
    )
    html = f"""<html><body style="font-family:sans-serif;margin:20px">
<div style="background:#1e293b;color:#fff;padding:16px;border-radius:8px;margin-bottom:16px">
  <h2 style="margin:0">IMF Pipeline — Monitoring</h2>
  <p style="margin:4px 0;opacity:.7">{datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}</p>
</div>
<div style="border-left:4px solid {color};padding:12px 16px;background:#f8fafc;border-radius:4px">
  <h3 style="margin:0 0 8px">{emoji} {subject}</h3>
  {body_html}
</div>
<p style="color:#64748b;font-size:12px;margin-top:16px">
  Host: {socket.gethostname()} · <a href="https://imf.rene.it.com">imf.rene.it.com</a>
</p>
</body></html>"""
    msg.attach(MIMEText(html, "html"))

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as s:
        s.starttls()
        s.login(SMTP_USER, SMTP_PASS)
        s.send_message(msg)


# ── Collecte d'incidents ───────────────────────────────────────────────────────
def collect_issues() -> list[dict]:
    issues = []

    # Containers
    try:
        out = subprocess.check_output(
            [
                "docker",
                "ps",
                "-a",
                "--format",
                "{{.Names}}\t{{.Status}}\t{{.RestartCount}}",
            ],
            text=True,
            timeout=10,
        )
        running = {}
        for line in out.strip().splitlines():
            parts = line.split("\t")
            if len(parts) >= 2:
                running[parts[0]] = (parts[1], int(parts[2]) if len(parts) > 2 else 0)
        for name in IMF_CONTAINERS:
            if name not in running:
                issues.append(
                    {
                        "key": f"ctr_{name}",
                        "sev": "CRITICAL",
                        "msg": f"Container <b>{name}</b> introuvable",
                    }
                )
            else:
                status, restarts = running[name]
                if not status.startswith("Up"):
                    issues.append(
                        {
                            "key": f"ctr_{name}",
                            "sev": "CRITICAL",
                            "msg": f"Container <b>{name}</b> DOWN ({status})",
                        }
                    )
                elif restarts > 5:
                    issues.append(
                        {
                            "key": f"ctr_{name}_rst",
                            "sev": "WARNING",
                            "msg": f"Container <b>{name}</b> : {restarts} redémarrages",
                        }
                    )
    except Exception as e:
        issues.append(
            {"key": "docker", "sev": "CRITICAL", "msg": f"Docker daemon : {e}"}
        )

    # HTTP
    import urllib.request

    for name, url, expected in HTTP_CHECKS:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "IMF-Monitor/1.0"})
            with urllib.request.urlopen(req, timeout=8) as r:
                if r.status != expected:
                    issues.append(
                        {
                            "key": f"http_{name}",
                            "sev": "WARNING",
                            "msg": f"<b>{name}</b> → HTTP {r.status}",
                        }
                    )
        except Exception as e:
            issues.append(
                {
                    "key": f"http_{name}",
                    "sev": "CRITICAL",
                    "msg": f"<b>{name}</b> inaccessible : {e}",
                }
            )

    # Disque
    for path in ["/", "/var", "/opt"]:
        if not Path(path).exists():
            continue
        u = shutil.disk_usage(path)
        pct = int(u.used / u.total * 100)
        free = u.free / 1e9
        if pct >= 90:
            issues.append(
                {
                    "key": f"disk{path}",
                    "sev": "CRITICAL",
                    "msg": f"Disque <b>{path}</b> : {pct}% ({free:.1f}GB libres)",
                }
            )
        elif pct >= 80:
            issues.append(
                {
                    "key": f"disk{path}",
                    "sev": "WARNING",
                    "msg": f"Disque <b>{path}</b> : {pct}% ({free:.1f}GB libres)",
                }
            )

    # RAM
    try:
        with open("/proc/meminfo") as f:
            info = {
                line.split(":")[0]: int(line.split()[1]) for line in f if ":" in line
            }
        total = info.get("MemTotal", 1)
        avail = info.get("MemAvailable", total)
        pct = int((1 - avail / total) * 100)
        if pct >= 95:
            issues.append(
                {
                    "key": "ram",
                    "sev": "CRITICAL",
                    "msg": f"RAM : {pct}% ({(total-avail)/1e6:.1f}/{total/1e6:.1f}GB)",
                }
            )
        elif pct >= 85:
            issues.append(
                {
                    "key": "ram",
                    "sev": "WARNING",
                    "msg": f"RAM : {pct}% ({(total-avail)/1e6:.1f}/{total/1e6:.1f}GB)",
                }
            )
    except Exception:
        pass

    # PostgreSQL TCP
    try:
        sock = socket.create_connection((PG_HOST, PG_PORT), timeout=8)
        sock.close()
    except Exception as e:
        issues.append(
            {
                "key": "postgres",
                "sev": "CRITICAL",
                "msg": f"PostgreSQL Supabase inaccessible ({PG_HOST}:{PG_PORT}) : {e}",
            }
        )

    return issues


# ── Tâche principale ───────────────────────────────────────────────────────────
def run_monitoring(**ctx) -> None:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    state = _load_state()

    issues = collect_issues()
    current = {i["key"]: i for i in issues}

    # Nouvelles alertes
    for key, issue in current.items():
        if key not in state["incidents"]:
            _send(
                subject=f"{issue['sev']} — {issue['msg'].replace('<b>', '').replace('</b>', '')}",
                body_html=f"<p>{issue['msg']}</p><p style='color:#64748b;font-size:12px'>Détecté : {now}</p>",
                severity=issue["sev"],
            )
            state["incidents"][key] = {"sev": issue["sev"], "since": now}
            print(f"[ALERT] {issue['sev']} : {key}")

    # Résolutions
    for key in [k for k in list(state["incidents"]) if k not in current]:
        prev = state["incidents"].pop(key)
        _send(
            subject=f"RÉSOLU — {key.replace('_', ' ')}",
            body_html=f"<p>Incident <b>{key}</b> résolu.</p>"
            f"<p style='color:#64748b;font-size:12px'>Début : {prev['since']} · Fin : {now}</p>",
            severity="OK",
        )
        print(f"[RESOLVED] {key}")

    _save_state(state)
    print(f"[{now}] {len(current)} incident(s) actif(s)")

    if current:
        raise Exception(f"Incidents actifs : {list(current.keys())}")


with DAG(
    dag_id="dag_monitoring",
    description="Health check système IMF Pipeline — alerte email",
    schedule_interval="*/5 * * * *",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    default_args=DEFAULT_ARGS,
    max_active_runs=1,
    tags=["monitoring", "alerting"],
) as dag:

    PythonOperator(
        task_id="health_check",
        python_callable=run_monitoring,
    )
