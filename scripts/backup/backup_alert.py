#!/usr/bin/env python3
"""Envoi d'alerte email en cas d'échec de backup."""
import os, smtplib, sys
from email.mime.text import MIMEText

subject = sys.argv[1] if len(sys.argv) > 1 else "ALERTE backup IMF"
body    = sys.argv[2] if len(sys.argv) > 2 else "Un backup a échoué."
to      = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("DSI_EMAIL", "renekomtsindi7@gmail.com")

msg = MIMEText(body)
msg["Subject"] = f"[IMF Pipeline] {subject}"
msg["From"]    = os.environ.get("SMTP_USER", "renekomtsindi7@gmail.com")
msg["To"]      = to

with smtplib.SMTP(os.environ.get("SMTP_HOST","smtp.gmail.com"), 587) as s:
    s.starttls()
    s.login(os.environ.get("SMTP_USER",""), os.environ.get("SMTP_PASSWORD",""))
    s.send_message(msg)
print(f"Alerte envoyée → {to}")
