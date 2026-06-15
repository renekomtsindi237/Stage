#!/usr/bin/env bash
# deploy_monitor.sh — Déploie le monitoring sur le VPS IMF
# Usage : bash scripts/monitoring/deploy_monitor.sh
set -euo pipefail

VPS_HOST="84.247.128.40"
VPS_PORT="2222"
VPS_USER="rene"
SSH_KEY="${HOME}/User/User/life/new life/Projets/Servant/vps_staging_ssh_key.pem"
REMOTE_DIR="/opt/imf/monitoring"
SMTP_PASS="kqihvnjqlyltxnwn"

SSH="ssh -i \"${SSH_KEY}\" -p ${VPS_PORT} -o StrictHostKeyChecking=no ${VPS_USER}@${VPS_HOST}"
SCP="scp -i \"${SSH_KEY}\" -P ${VPS_PORT} -o StrictHostKeyChecking=no"

echo "=== Déploiement du monitoring IMF ==="

# 1. Copier le script
eval "${SCP} scripts/monitoring/monitor.py ${VPS_USER}@${VPS_HOST}:${REMOTE_DIR}/monitor.py" 2>/dev/null || {
    # Créer le dossier si nécessaire
    eval "${SSH} 'mkdir -p ${REMOTE_DIR}'"
    eval "${SCP} scripts/monitoring/monitor.py ${VPS_USER}@${VPS_HOST}:${REMOTE_DIR}/monitor.py"
}

# 2. Configurer et activer le cron toutes les 5 minutes
eval "${SSH}" << ENDSSH
set -e

sudo mkdir -p /var/lib/imf-monitor
sudo chown ${VPS_USER}:${VPS_USER} /var/lib/imf-monitor

# Wrapper avec variables d'env
cat > ${REMOTE_DIR}/run_monitor.sh << 'EOF'
#!/usr/bin/env bash
export SMTP_PASSWORD="${SMTP_PASS}"
export SMTP_USER="renekomtsindi7@gmail.com"
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export ALERT_EMAIL="renekomtsindi7@gmail.com"

# Charger les variables depuis .env si disponible
if [ -f /opt/imf/.env ]; then
    while IFS= read -r line; do
        [[ "\$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "\${line//[[:space:]]/}" ]] && continue
        key="\${line%%=*}"
        val="\${line#*=}"
        export "\$key=\$val" 2>/dev/null || true
    done < /opt/imf/.env
fi
export SMTP_PASSWORD="${SMTP_PASS}"

/usr/bin/python3 ${REMOTE_DIR}/monitor.py >> /var/log/imf-monitor.log 2>&1
EOF
chmod +x ${REMOTE_DIR}/run_monitor.sh

# Cron toutes les 5 minutes (utilisateur courant, pas root)
(crontab -l 2>/dev/null | grep -v "run_monitor.sh" ; echo "*/5 * * * * ${REMOTE_DIR}/run_monitor.sh") | crontab -

echo "Monitoring déployé — cron actif (*/5 * * * *)"
crontab -l | grep monitor
ENDSSH

echo ""
echo "=== Déploiement terminé ==="
echo "Logs : ssh -i '${SSH_KEY}' -p ${VPS_PORT} ${VPS_USER}@${VPS_HOST} 'tail -f /var/log/imf-monitor.log'"
