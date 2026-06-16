#!/usr/bin/env python3
"""
IMF Pipeline — Monitoring & Alerting

Modes :
  python3 monitor.py          → vérification horaire (alerte immédiate si anomalie)
  python3 monitor.py --report → rapport quotidien complet (envoyé à 07h00 UTC)

Logique :
  - Vérification toutes les heures via cron
  - Alerte email immédiate dès qu'une anomalie est détectée (déduplication)
  - Email de résolution quand le service revient
  - Rapport quotidien à 07h00 UTC : état de tous les containers + métriques système

State persisté dans /var/lib/imf-monitor/state.json
"""
import json
import os
import shutil
import smtplib
import socket
import subprocess
import sys
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from pathlib import Path

# ── Config ────────────────────────────────────────────────────────────────────
ALERT_TO   = os.getenv("ALERT_EMAIL",   "renekomtsindi7@gmail.com")
SMTP_HOST  = os.getenv("SMTP_HOST",     "smtp.gmail.com")
SMTP_PORT  = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER  = os.getenv("SMTP_USER",     "renekomtsindi7@gmail.com")
SMTP_PASS  = os.getenv("SMTP_PASSWORD", "")
STATE_FILE = Path("/var/lib/imf-monitor/state.json")

DISK_WARN  = 80
DISK_CRIT  = 90
RAM_WARN   = 85
RAM_CRIT   = 95
CPU_WARN   = 90

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
    ("Backend health", "http://127.0.0.1:9200/actuator/health", 200),
    ("Frontend nginx", "http://127.0.0.1:9091/",                200),
    ("API nginx",      "http://127.0.0.1:9090/",                None),
    ("ML API",         "http://127.0.0.1:8090/",                None),
]


# ── State ─────────────────────────────────────────────────────────────────────
def load_state() -> dict:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text())
        except Exception:
            pass
    return {"incidents": {}, "cpu_high_cycles": 0, "cpu_prev": {}}


def save_state(state: dict) -> None:
    STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2))


# ── Email ─────────────────────────────────────────────────────────────────────
def _send_email(subject: str, body_html: str, severity: str = "INFO") -> bool:
    emoji = {"CRITICAL": "🔴", "WARNING": "🟡", "OK": "🟢", "INFO": "📊"}.get(severity, "⚪")
    border = {"CRITICAL": "#ef4444", "WARNING": "#f59e0b", "OK": "#22c55e", "INFO": "#3b82f6"}.get(severity, "#94a3b8")

    msg = MIMEMultipart("alternative")
    msg["Subject"] = f"{emoji} [IMF Pipeline] {subject}"
    msg["From"]    = SMTP_USER
    msg["To"]      = ALERT_TO

    html = f"""<html><body style="font-family:Arial,sans-serif;margin:0;background:#f1f5f9">
<div style="max-width:680px;margin:24px auto;background:white;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1)">
  <div style="background:#1e293b;color:white;padding:20px 24px">
    <h2 style="margin:0;font-size:18px">IMF Pipeline — Monitor</h2>
    <p style="margin:4px 0 0;opacity:.6;font-size:13px">{datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')} · {socket.gethostname()}</p>
  </div>
  <div style="padding:24px">
    <div style="border-left:4px solid {border};padding:12px 16px;background:#f8fafc;border-radius:4px;margin-bottom:16px">
      <h3 style="margin:0 0 8px;font-size:15px">{emoji} {subject}</h3>
      {body_html}
    </div>
    <p style="color:#94a3b8;font-size:11px;margin:0">
      <a href="https://imf.rene.it.com" style="color:#3b82f6">imf.rene.it.com</a> ·
      <a href="http://84.247.128.40:8090" style="color:#3b82f6">Airflow</a>
    </p>
  </div>
</div>
</body></html>"""

    msg.attach(MIMEText(html, "html"))
    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as s:
            s.starttls()
            s.login(SMTP_USER, SMTP_PASS)
            s.send_message(msg)
        print(f"[{severity}] Email envoyé : {subject}")
        return True
    except Exception as e:
        print(f"[ERROR] Email failed : {e}")
        return False


# ── Collecte de métriques ──────────────────────────────────────────────────────
def _container_logs(name: str, lines: int = 30) -> str:
    try:
        return subprocess.check_output(
            ["docker", "logs", "--tail", str(lines), name],
            text=True, stderr=subprocess.STDOUT, timeout=10,
        ).strip()
    except Exception as e:
        return f"(logs indisponibles : {e})"


def collect_containers() -> tuple[list[dict], list[dict]]:
    """Retourne (issues, all_statuses) pour les containers IMF."""
    issues, statuses = [], []
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
                                "msg": f"Container <b>{cname}</b> introuvable", "logs": ""})
                statuses.append({"name": cname, "status": "MISSING", "ok": False})
            else:
                status = running[cname]
                ok = status.startswith("Up")
                statuses.append({"name": cname, "status": status, "ok": ok})
                if not ok:
                    issues.append({"key": f"container_{cname}", "severity": "CRITICAL",
                                   "msg": f"Container <b>{cname}</b> DOWN — {status}",
                                   "logs": _container_logs(cname)})
    except Exception as e:
        issues.append({"key": "docker_daemon", "severity": "CRITICAL",
                       "msg": f"Docker daemon inaccessible : {e}", "logs": ""})
    return issues, statuses


def collect_http() -> tuple[list[dict], list[dict]]:
    import urllib.request, urllib.error
    issues, statuses = [], []
    for name, url, expected in HTTP_CHECKS:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "IMF-Monitor/1.0"})
            try:
                with urllib.request.urlopen(req, timeout=8) as r:
                    code = r.status
            except urllib.error.HTTPError as e:
                code = e.code
            ok = (code < 500) if expected is None else (code == expected)
            statuses.append({"name": name, "code": code, "ok": ok})
            if not ok:
                issues.append({"key": f"http_{name}", "severity": "CRITICAL",
                               "msg": f"<b>{name}</b> — HTTP {code}"})
        except Exception as e:
            statuses.append({"name": name, "code": 0, "ok": False})
            issues.append({"key": f"http_{name}", "severity": "CRITICAL",
                           "msg": f"<b>{name}</b> inaccessible : {e}"})
    return issues, statuses


def collect_system() -> tuple[list[dict], dict]:
    issues = []
    metrics = {}

    # Disque
    disk_rows = []
    for path in ["/", "/var", "/opt"]:
        if not Path(path).exists():
            continue
        u = shutil.disk_usage(path)
        pct = int(u.used / u.total * 100)
        free = u.free / 1e9
        disk_rows.append({"path": path, "pct": pct, "free_gb": round(free, 1)})
        if pct >= DISK_CRIT:
            issues.append({"key": f"disk_{path}", "severity": "CRITICAL",
                           "msg": f"Disque <b>{path}</b> : {pct}% ({free:.1f}GB libres)"})
        elif pct >= DISK_WARN:
            issues.append({"key": f"disk_{path}", "severity": "WARNING",
                           "msg": f"Disque <b>{path}</b> : {pct}% ({free:.1f}GB libres)"})
    metrics["disk"] = disk_rows

    # RAM
    try:
        with open("/proc/meminfo") as f:
            info = {l.split(":")[0]: int(l.split()[1]) for l in f if ":" in l}
        total = info.get("MemTotal", 1)
        avail = info.get("MemAvailable", total)
        pct   = int((1 - avail / total) * 100)
        metrics["ram"] = {"pct": pct, "used_gb": round((total - avail) / 1e6, 1),
                          "total_gb": round(total / 1e6, 1)}
        if pct >= RAM_CRIT:
            issues.append({"key": "memory", "severity": "CRITICAL",
                           "msg": f"RAM : {pct}% ({metrics['ram']['used_gb']}/{metrics['ram']['total_gb']}GB)"})
        elif pct >= RAM_WARN:
            issues.append({"key": "memory", "severity": "WARNING",
                           "msg": f"RAM : {pct}% ({metrics['ram']['used_gb']}/{metrics['ram']['total_gb']}GB)"})
    except Exception:
        metrics["ram"] = {}

    # Charge CPU (load average 1min)
    try:
        load1 = os.getloadavg()[0]
        cpu_count = os.cpu_count() or 1
        load_pct = int(load1 / cpu_count * 100)
        metrics["cpu"] = {"load1": round(load1, 2), "pct": load_pct}
        if load_pct >= CPU_WARN:
            issues.append({"key": "cpu", "severity": "WARNING",
                           "msg": f"CPU load : {load1:.2f} ({load_pct}% sur {cpu_count} cœurs)"})
    except Exception:
        metrics["cpu"] = {}

    # PostgreSQL
    try:
        pg_host = os.getenv("POSTGRES_HOST", "aws-0-eu-west-3.pooler.supabase.com")
        pg_port = int(os.getenv("POSTGRES_PORT", "6543"))
        sock = socket.create_connection((pg_host, pg_port), timeout=8)
        sock.close()
        metrics["postgres"] = {"ok": True}
    except Exception as e:
        metrics["postgres"] = {"ok": False, "error": str(e)}
        issues.append({"key": "postgres_tcp", "severity": "CRITICAL",
                       "msg": f"PostgreSQL Supabase inaccessible : {e}"})

    return issues, metrics


# ── Mode alerte immédiate ──────────────────────────────────────────────────────
def run_check() -> None:
    state = load_state()
    now   = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    ctr_issues, _ = collect_containers()
    http_issues, _ = collect_http()
    sys_issues, _  = collect_system()
    all_issues = ctr_issues + http_issues + sys_issues

    current = {i["key"]: i for i in all_issues}

    # Nouvelles anomalies → alerte immédiate
    for key, issue in current.items():
        if key not in state["incidents"]:
            logs_html = ""
            if issue.get("logs"):
                logs = issue["logs"].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                logs_html = (
                    "<p style='margin-top:12px'><b>Derniers logs :</b></p>"
                    "<pre style='background:#0f172a;color:#e2e8f0;padding:12px;"
                    "border-radius:6px;font-size:11px;white-space:pre-wrap'>"
                    f"{logs}</pre>"
                )
            _send_email(
                subject=f"{issue['severity']} — {issue['msg'].replace('<b>','').replace('</b>','')}",
                body_html=(f"<p>{issue['msg']}</p>"
                           f"<p style='color:#64748b;font-size:12px'>Détecté : {now}</p>"
                           f"{logs_html}"),
                severity=issue["severity"],
            )
            state["incidents"][key] = {"severity": issue["severity"], "since": now}

    # Résolutions → email immédiat
    for key in [k for k in list(state["incidents"]) if k not in current]:
        prev = state["incidents"].pop(key)
        _send_email(
            subject=f"RÉSOLU — {key.replace('_', ' ')}",
            body_html=(f"<p>Incident <b>{key}</b> résolu.</p>"
                       f"<p style='color:#64748b;font-size:12px'>"
                       f"Début : {prev['since']} · Fin : {now}</p>"),
            severity="OK",
        )

    save_state(state)
    print(f"[{now}] Check terminé — {len(current)} incident(s) actif(s)")


# ── Mode rapport quotidien ─────────────────────────────────────────────────────
def run_report() -> None:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    date = datetime.now(timezone.utc).strftime("%d/%m/%Y")

    _, ctr_statuses = collect_containers()
    _, http_statuses = collect_http()
    _, metrics = collect_system()
    state = load_state()
    active_incidents = state.get("incidents", {})

    # ── Containers ──
    ctr_rows = ""
    for c in ctr_statuses:
        icon = "🟢" if c["ok"] else "🔴"
        color = "#166534" if c["ok"] else "#991b1b"
        bg    = "#f0fdf4" if c["ok"] else "#fef2f2"
        ctr_rows += (
            f"<tr style='background:{bg}'>"
            f"<td style='padding:8px 12px;font-family:monospace;font-size:13px'>{c['name']}</td>"
            f"<td style='padding:8px 12px;color:{color};font-size:13px'>{icon} {c['status']}</td>"
            f"</tr>"
        )

    # ── HTTP ──
    http_rows = ""
    for h in http_statuses:
        icon = "🟢" if h["ok"] else "🔴"
        color = "#166534" if h["ok"] else "#991b1b"
        bg    = "#f0fdf4" if h["ok"] else "#fef2f2"
        http_rows += (
            f"<tr style='background:{bg}'>"
            f"<td style='padding:8px 12px;font-size:13px'>{h['name']}</td>"
            f"<td style='padding:8px 12px;color:{color};font-size:13px'>{icon} HTTP {h['code']}</td>"
            f"</tr>"
        )

    # ── Système ──
    ram   = metrics.get("ram", {})
    cpu   = metrics.get("cpu", {})
    pg    = metrics.get("postgres", {})
    disk  = metrics.get("disk", [])

    ram_pct   = ram.get("pct", "?")
    cpu_load  = cpu.get("load1", "?")
    pg_status = "🟢 Joignable" if pg.get("ok") else "🔴 Inaccessible"

    disk_rows = "".join(
        f"<tr><td style='padding:6px 12px;font-family:monospace;font-size:13px'>{d['path']}</td>"
        f"<td style='padding:6px 12px;font-size:13px'>{'🔴' if d['pct']>=90 else '🟡' if d['pct']>=80 else '🟢'} "
        f"{d['pct']}% ({d['free_gb']}GB libres)</td></tr>"
        for d in disk
    )

    # ── Incidents actifs ──
    if active_incidents:
        inc_html = "<ul style='margin:8px 0;padding-left:20px'>" + "".join(
            f"<li style='color:#991b1b;font-size:13px'><b>{k}</b> depuis {v['since']}</li>"
            for k, v in active_incidents.items()
        ) + "</ul>"
    else:
        inc_html = "<p style='color:#166534;font-size:13px;margin:4px 0'>✅ Aucun incident actif</p>"

    body = f"""
<h3 style="margin:0 0 16px;color:#1e293b">Rapport du {date}</h3>

<h4 style="margin:16px 0 8px;color:#374151">Containers IMF</h4>
<table style="width:100%;border-collapse:collapse;border-radius:8px;overflow:hidden;border:1px solid #e2e8f0">
  <tr style="background:#f8fafc"><th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">Container</th>
  <th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">Statut</th></tr>
  {ctr_rows}
</table>

<h4 style="margin:16px 0 8px;color:#374151">Endpoints HTTP</h4>
<table style="width:100%;border-collapse:collapse;border-radius:8px;overflow:hidden;border:1px solid #e2e8f0">
  <tr style="background:#f8fafc"><th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">Service</th>
  <th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">Réponse</th></tr>
  {http_rows}
</table>

<h4 style="margin:16px 0 8px;color:#374151">Métriques système</h4>
<table style="width:100%;border-collapse:collapse;border-radius:8px;overflow:hidden;border:1px solid #e2e8f0">
  <tr style="background:#f8fafc"><th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">Ressource</th>
  <th style="padding:8px 12px;text-align:left;font-size:12px;color:#64748b">État</th></tr>
  <tr><td style="padding:8px 12px;font-size:13px">RAM</td>
      <td style="padding:8px 12px;font-size:13px">{'🔴' if isinstance(ram_pct,int) and ram_pct>=95 else '🟡' if isinstance(ram_pct,int) and ram_pct>=85 else '🟢'} {ram_pct}% ({ram.get('used_gb','?')}/{ram.get('total_gb','?')} GB)</td></tr>
  <tr style="background:#f8fafc"><td style="padding:8px 12px;font-size:13px">CPU Load</td>
      <td style="padding:8px 12px;font-size:13px">🟢 {cpu_load}</td></tr>
  {disk_rows}
  <tr><td style="padding:8px 12px;font-size:13px">PostgreSQL Supabase</td>
      <td style="padding:8px 12px;font-size:13px">{pg_status}</td></tr>
</table>

<h4 style="margin:16px 0 8px;color:#374151">Incidents actifs</h4>
<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid #e2e8f0">
  {inc_html}
</div>
"""

    all_ok = all(c["ok"] for c in ctr_statuses) and all(h["ok"] for h in http_statuses) and not active_incidents
    _send_email(
        subject=f"Rapport quotidien {date} — {'✅ Tout est OK' if all_ok else '⚠️ Anomalies détectées'}",
        body_html=body,
        severity="INFO" if all_ok else "WARNING",
    )
    print(f"[{now}] Rapport quotidien envoyé")


# ── Entrée ────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    if "--report" in sys.argv:
        run_report()
    else:
        run_check()
