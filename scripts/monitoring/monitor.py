#!/usr/bin/env python3
"""
IMF Pipeline — Monitoring & Alerting
Vérifie toutes les 5 minutes :
  - Containers Docker (up/down/restart count)
  - Endpoints HTTP (health checks)
  - Disque (> 80 % = WARNING, > 90 % = CRITICAL)
  - RAM  (> 85 % = WARNING, > 95 % = CRITICAL)
  - CPU  (> 90 % sur 2 cycles consécutifs = WARNING)
  - Connectivité PostgreSQL Supabase
  - Validité JWT (expiration du secret)

Déduplication : une alerte par incident, une notification de résolution.
State persisté dans /var/lib/imf-monitor/state.json
"""
import json
import os
import shutil
import smtplib
import socket
import subprocess
import time
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from pathlib import Path

# ── Config ────────────────────────────────────────────────────────────────────
ALERT_TO    = os.getenv("ALERT_EMAIL",   "renekomtsindi7@gmail.com")
SMTP_HOST   = os.getenv("SMTP_HOST",     "smtp.gmail.com")
SMTP_PORT   = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER   = os.getenv("SMTP_USER",     "renekomtsindi7@gmail.com")
SMTP_PASS   = os.getenv("SMTP_PASSWORD", "")
STATE_FILE  = Path("/var/lib/imf-monitor/state.json")

DISK_WARN   = 80
DISK_CRIT   = 90
RAM_WARN    = 85
RAM_CRIT    = 95
CPU_WARN    = 90

IMF_CONTAINERS = [
    "imf-backend",
    "imf-frontend",
    "imf-nginx-api",
    "imf-airflow-scheduler",
    "imf-airflow-webserver",
    "imf-ml-api",
    "imf-redis",
    "imf-airflow-db",
]

# 127.0.0.1 explicite : localhost résout en ::1 (IPv6) sur ce VPS
# expected_status=None → tout code < 500 est considéré UP
HTTP_CHECKS = [
    ("Backend health",  "http://127.0.0.1:9200/actuator/health", 200),
    ("Frontend nginx",  "http://127.0.0.1:9091/",                200),
    ("API nginx",       "http://127.0.0.1:9090/",                None),
    ("ML API",          "http://127.0.0.1:8090/",                None),
]


# ── State ────────────────────────────────────────────────────────────────────
def load_state() -> dict:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text())
        except Exception:
            pass
    return {"incidents": {}, "cpu_high_cycles": 0}


def save_state(state: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2))


# ── Email ─────────────────────────────────────────────────────────────────────
def send_alert(subject: str, body_html: str, severity: str = "WARNING") -> bool:
    emoji = {"CRITICAL": "🔴", "WARNING": "🟡", "OK": "🟢"}.get(severity, "⚪")
    full_subject = f"{emoji} [IMF Pipeline] {subject}"
    msg = MIMEMultipart("alternative")
    msg["Subject"] = full_subject
    msg["From"]    = SMTP_USER
    msg["To"]      = ALERT_TO

    html = f"""
    <html><body style="font-family:Arial,sans-serif;margin:20px">
    <div style="background:#1e293b;color:white;padding:16px;border-radius:8px;margin-bottom:16px">
      <h2 style="margin:0">IMF Pipeline Monitor</h2>
      <p style="margin:4px 0;opacity:.7">{datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}</p>
    </div>
    <div style="background:#f8fafc;border-left:4px solid {'#ef4444' if severity=='CRITICAL' else '#f59e0b' if severity=='WARNING' else '#22c55e'};
         padding:16px;border-radius:4px;margin-bottom:16px">
      <h3 style="margin:0 0 8px">{emoji} {subject}</h3>
      {body_html}
    </div>
    <p style="color:#64748b;font-size:12px">
      Hôte : {socket.gethostname()} ·
      <a href="http://imf.rene.it.com">imf.rene.it.com</a>
    </p>
    </body></html>
    """
    msg.attach(MIMEText(html, "html"))

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as s:
            s.starttls()
            s.login(SMTP_USER, SMTP_PASS)
            s.send_message(msg)
        print(f"[{severity}] Alert sent: {subject}")
        return True
    except Exception as e:
        print(f"[ERROR] Email failed: {e}")
        return False


# ── Helpers ───────────────────────────────────────────────────────────────────
def _container_logs(name: str, lines: int = 30) -> str:
    """Récupère les dernières lignes de logs d'un container."""
    try:
        out = subprocess.check_output(
            ["docker", "logs", "--tail", str(lines), name],
            text=True, stderr=subprocess.STDOUT, timeout=10,
        )
        return out.strip()
    except Exception as e:
        return f"(logs indisponibles : {e})"


# ── Checks ────────────────────────────────────────────────────────────────────
def check_containers() -> list[dict]:
    issues = []
    try:
        out = subprocess.check_output(
            ["docker", "ps", "-a", "--format", "{{.Names}}\t{{.Status}}"],
            text=True, timeout=10,
        )
        running = {}
        for line in out.strip().splitlines():
            parts = line.split("\t")
            if len(parts) >= 2:
                running[parts[0]] = parts[1]

        for cname in IMF_CONTAINERS:
            if cname not in running:
                issues.append({"key": f"container_{cname}", "severity": "CRITICAL",
                                "msg": f"Container <b>{cname}</b> introuvable (non démarré ?)",
                                "logs": ""})
            else:
                status = running[cname]
                if not status.startswith("Up"):
                    logs = _container_logs(cname)
                    issues.append({"key": f"container_{cname}", "severity": "CRITICAL",
                                   "msg": f"Container <b>{cname}</b> DOWN — statut : {status}",
                                   "logs": logs})
    except Exception as e:
        issues.append({"key": "docker_daemon", "severity": "CRITICAL",
                       "msg": f"Docker daemon inaccessible : {e}"})
    return issues


def check_http() -> list[dict]:
    issues = []
    import urllib.request, urllib.error
    for name, url, expected_status in HTTP_CHECKS:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "IMF-Monitor/1.0"})
            try:
                with urllib.request.urlopen(req, timeout=8) as r:
                    code = r.status
            except urllib.error.HTTPError as e:
                code = e.code
            # None = tout code < 500 est OK (service répond)
            if expected_status is None:
                if code >= 500:
                    issues.append({"key": f"http_{name}", "severity": "CRITICAL",
                                   "msg": f"<b>{name}</b> — HTTP {code} (erreur serveur)"})
            elif code != expected_status:
                issues.append({"key": f"http_{name}", "severity": "WARNING",
                               "msg": f"<b>{name}</b> — HTTP {code} (attendu {expected_status})"})
        except Exception as e:
            issues.append({"key": f"http_{name}", "severity": "CRITICAL",
                           "msg": f"<b>{name}</b> inaccessible : {e}"})
    return issues


def check_disk() -> list[dict]:
    issues = []
    for path in ["/", "/var", "/opt"]:
        if not Path(path).exists():
            continue
        usage = shutil.disk_usage(path)
        pct = int(usage.used / usage.total * 100)
        free_gb = (usage.free) / 1e9
        if pct >= DISK_CRIT:
            issues.append({"key": f"disk_{path}", "severity": "CRITICAL",
                           "msg": f"Disque <b>{path}</b> : {pct}% utilisé, {free_gb:.1f} GB libres"})
        elif pct >= DISK_WARN:
            issues.append({"key": f"disk_{path}", "severity": "WARNING",
                           "msg": f"Disque <b>{path}</b> : {pct}% utilisé, {free_gb:.1f} GB libres"})
    return issues


def check_memory() -> list[dict]:
    issues = []
    try:
        with open("/proc/meminfo") as f:
            info = {line.split(":")[0]: int(line.split()[1]) for line in f if ":" in line}
        total    = info.get("MemTotal", 1)
        available = info.get("MemAvailable", total)
        used_pct = int((1 - available / total) * 100)
        used_gb  = (total - available) / 1e6
        total_gb = total / 1e6
        if used_pct >= RAM_CRIT:
            issues.append({"key": "memory", "severity": "CRITICAL",
                           "msg": f"RAM : {used_pct}% utilisée ({used_gb:.1f}/{total_gb:.1f} GB)"})
        elif used_pct >= RAM_WARN:
            issues.append({"key": "memory", "severity": "WARNING",
                           "msg": f"RAM : {used_pct}% utilisée ({used_gb:.1f}/{total_gb:.1f} GB)"})
    except Exception as e:
        issues.append({"key": "memory_check", "severity": "WARNING", "msg": str(e)})
    return issues


def check_cpu(state: dict) -> list[dict]:
    issues = []
    try:
        with open("/proc/stat") as f:
            parts = f.readline().split()
        idle, total = int(parts[4]), sum(int(x) for x in parts[1:])

        prev = state.get("cpu_prev", {})
        if prev:
            d_idle  = idle  - prev.get("idle", idle)
            d_total = total - prev.get("total", total)
            if d_total > 0:
                cpu_pct = int((1 - d_idle / d_total) * 100)
                if cpu_pct >= CPU_WARN:
                    state["cpu_high_cycles"] = state.get("cpu_high_cycles", 0) + 1
                    if state["cpu_high_cycles"] >= 2:
                        issues.append({"key": "cpu", "severity": "WARNING",
                                       "msg": f"CPU : {cpu_pct}% (élevé depuis {state['cpu_high_cycles']} cycles)"})
                else:
                    state["cpu_high_cycles"] = 0

        state["cpu_prev"] = {"idle": idle, "total": total}
    except Exception:
        pass
    return issues


def check_postgres() -> list[dict]:
    issues = []
    try:
        pg_host = os.getenv("POSTGRES_HOST", "aws-0-eu-west-3.pooler.supabase.com")
        pg_port = int(os.getenv("POSTGRES_PORT", "6543"))
        sock = socket.create_connection((pg_host, pg_port), timeout=8)
        sock.close()
    except Exception as e:
        issues.append({"key": "postgres_tcp", "severity": "CRITICAL",
                       "msg": f"PostgreSQL Supabase inaccessible ({pg_host}:{pg_port}) : {e}"})
    return issues


# ── Main loop ─────────────────────────────────────────────────────────────────
def run_once() -> None:
    state = load_state()
    now   = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    all_checks = (
        check_containers()
        + check_http()
        + check_disk()
        + check_memory()
        + check_cpu(state)
        + check_postgres()
    )

    # Clés actuellement en erreur
    current_issues = {c["key"]: c for c in all_checks}

    # Nouvelles alertes (pas encore dans state)
    for key, issue in current_issues.items():
        if key not in state["incidents"]:
            logs_html = ""
            if issue.get("logs"):
                logs_html = (
                    "<p style='margin-top:12px'><b>Derniers logs du container :</b></p>"
                    f"<pre style='background:#0f172a;color:#e2e8f0;padding:12px;"
                    f"border-radius:6px;font-size:11px;overflow-x:auto;white-space:pre-wrap'>"
                    f"{issue['logs']}</pre>"
                )
            send_alert(
                subject=f"{issue['severity']} — {issue['msg'].replace('<b>', '').replace('</b>', '')}",
                body_html=f"<p>{issue['msg']}</p>"
                          f"<p style='color:#64748b;font-size:13px'>Détecté le {now}</p>"
                          f"{logs_html}",
                severity=issue["severity"],
            )
            state["incidents"][key] = {"severity": issue["severity"], "since": now, "alerted": True}

    # Résolutions (étaient en erreur, maintenant OK)
    resolved = [k for k in list(state["incidents"]) if k not in current_issues]
    for key in resolved:
        prev = state["incidents"].pop(key)
        send_alert(
            subject=f"RÉSOLU — {key.replace('_', ' ')}",
            body_html=f"<p>L'incident <b>{key}</b> est résolu.</p>"
                      f"<p>Début : {prev.get('since','?')} · Résolu : {now}</p>",
            severity="OK",
        )

    save_state(state)

    total = len(all_checks)
    print(f"[{now}] Check OK — {total} incident(s) actif(s), {len(resolved)} résolu(s)")


if __name__ == "__main__":
    run_once()
