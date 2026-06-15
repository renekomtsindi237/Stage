#!/usr/bin/env bash
# ==============================================================================
# IMF Pipeline — Backup Enterprise vers Cloudflare R2
# Rotation GFS : 7 quotidiens · 4 hebdomadaires · 3 mensuels
# Usage : ./backup.sh [daily|weekly|monthly]   (défaut: daily)
# ==============================================================================
set -euo pipefail

SCOPE="${1:-daily}"
TIMESTAMP=$(date +%Y-%m-%dT%H-%M-%S)
DATE=$(date +%Y-%m-%d)
DOW=$(date +%u)   # 1=lundi … 7=dimanche
DOM=$(date +%d)   # jour du mois

# ── Config ────────────────────────────────────────────────────────────────────
BUCKET="r2-imf:imf-ml"
BACKUP_ROOT="${BUCKET}/backups"
LOG_FILE="/var/log/imf-backup.log"
WORK_DIR=$(mktemp -d /tmp/imf-backup-XXXXXX)
MANIFEST="${WORK_DIR}/manifest.json"

# Supabase
PG_HOST="${POSTGRES_HOST:-aws-0-eu-west-3.pooler.supabase.com}"
PG_PORT="${POSTGRES_PORT:-6543}"
PG_USER="${POSTGRES_USER:-postgres.ceiqkvvacjsakycsgcfz}"
PG_PASS="${POSTGRES_PASSWORD}"
PG_DB="${POSTGRES_DB:-postgres}"

SMTP_HOST_CFG="${SMTP_HOST:-smtp.gmail.com}"
SMTP_USER_CFG="${SMTP_USER:-renekomtsindi7@gmail.com}"
SMTP_PASS_CFG="${SMTP_PASSWORD}"
ALERT_TO="${DSI_EMAIL:-renekomtsindi7@gmail.com}"

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG_FILE"; }

cleanup() {
  rm -rf "$WORK_DIR"
  if [[ ${BACKUP_FAILED:-0} -eq 1 ]]; then
    log "ERREUR — envoi alerte email"
    python3 /opt/imf/backup_alert.py "ÉCHEC backup $SCOPE $DATE" \
      "Le backup IMF du $DATE ($SCOPE) a échoué. Vérifiez $LOG_FILE." \
      "$ALERT_TO" || true
  fi
}
trap cleanup EXIT

BACKUP_FAILED=0

log "=== Démarrage backup $SCOPE — $TIMESTAMP ==="

# ── 1. Dump PostgreSQL (schémas app + ml + dw) ───────────────────────────────
log "→ Dump PostgreSQL (Supabase)"
PG_DUMP_FILE="${WORK_DIR}/postgres_${SCOPE}_${TIMESTAMP}.sql.gz"
PGPASSWORD="$PG_PASS" pg_dump \
  -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
  --schema=app --schema=ml --schema=dw \
  --no-password --clean --if-exists \
  2>>"$LOG_FILE" | gzip -9 > "$PG_DUMP_FILE"

PG_SIZE=$(du -sh "$PG_DUMP_FILE" | cut -f1)
PG_SHA=$(sha256sum "$PG_DUMP_FILE" | cut -d' ' -f1)
log "  PostgreSQL → $PG_SIZE (sha256: ${PG_SHA:0:16}...)"

# ── 2. Modèles ML ─────────────────────────────────────────────────────────────
log "→ Archive modèles ML"
ML_ARCHIVE="${WORK_DIR}/ml_models_${SCOPE}_${TIMESTAMP}.tar.gz"
if [[ -d /ml/models ]]; then
  tar -czf "$ML_ARCHIVE" -C /ml models/ 2>>"$LOG_FILE"
  ML_SIZE=$(du -sh "$ML_ARCHIVE" | cut -f1)
  ML_SHA=$(sha256sum "$ML_ARCHIVE" | cut -d' ' -f1)
  log "  ML models → $ML_SIZE (sha256: ${ML_SHA:0:16}...)"
else
  # Récupérer depuis les containers Docker si /ml n'est pas monté
  docker cp imf-ml-api:/ml/models "${WORK_DIR}/models" 2>>"$LOG_FILE" || true
  [[ -d "${WORK_DIR}/models" ]] && tar -czf "$ML_ARCHIVE" -C "$WORK_DIR" models/ 2>>"$LOG_FILE"
  ML_SHA=$(sha256sum "$ML_ARCHIVE" 2>/dev/null | cut -d' ' -f1 || echo "absent")
  ML_SIZE=$(du -sh "$ML_ARCHIVE" 2>/dev/null | cut -f1 || echo "0")
  log "  ML models (container) → $ML_SIZE"
fi

# ── 3. Snapshot Redis ─────────────────────────────────────────────────────────
log "→ Snapshot Redis"
REDIS_FILE="${WORK_DIR}/redis_${SCOPE}_${TIMESTAMP}.rdb.gz"
REDIS_PASS="${REDIS_PASSWORD:-staging_redis_pass}"
docker exec imf_staging_redis redis-cli -a "$REDIS_PASS" --no-auth-warning BGSAVE 2>>"$LOG_FILE" || true
sleep 3
docker cp imf_staging_redis:/data/dump.rdb "${WORK_DIR}/dump.rdb" 2>>"$LOG_FILE" || true
if [[ -f "${WORK_DIR}/dump.rdb" ]]; then
  gzip -9 -c "${WORK_DIR}/dump.rdb" > "$REDIS_FILE"
  REDIS_SHA=$(sha256sum "$REDIS_FILE" | cut -d' ' -f1)
  log "  Redis → $(du -sh "$REDIS_FILE" | cut -f1)"
else
  REDIS_SHA="absent"
  log "  Redis → snapshot non disponible"
fi

# ── 4. DAGs Airflow ───────────────────────────────────────────────────────────
log "→ Archive DAGs Airflow"
DAGS_ARCHIVE="${WORK_DIR}/airflow_dags_${SCOPE}_${TIMESTAMP}.tar.gz"
if [[ -d /opt/imf/pipeline/dags ]]; then
  tar -czf "$DAGS_ARCHIVE" -C /opt/imf/pipeline dags/ 2>>"$LOG_FILE"
else
  docker cp imf_staging_airflow_sched:/opt/airflow/dags "${WORK_DIR}/dags" 2>>"$LOG_FILE" || true
  [[ -d "${WORK_DIR}/dags" ]] && tar -czf "$DAGS_ARCHIVE" -C "$WORK_DIR" dags/ || true
fi
DAGS_SHA=$(sha256sum "$DAGS_ARCHIVE" 2>/dev/null | cut -d' ' -f1 || echo "absent")

# ── 5. Manifeste JSON ─────────────────────────────────────────────────────────
log "→ Génération du manifeste"
cat > "$MANIFEST" << JSONEOF
{
  "backup_id": "${TIMESTAMP}",
  "scope": "${SCOPE}",
  "date": "${DATE}",
  "host": "$(hostname)",
  "artefacts": {
    "postgres": {
      "file": "$(basename "$PG_DUMP_FILE")",
      "sha256": "${PG_SHA}",
      "size": "${PG_SIZE}"
    },
    "ml_models": {
      "file": "$(basename "$ML_ARCHIVE" 2>/dev/null || echo '')",
      "sha256": "${ML_SHA}",
      "size": "${ML_SIZE}"
    },
    "redis": {
      "file": "$(basename "$REDIS_FILE" 2>/dev/null || echo '')",
      "sha256": "${REDIS_SHA}"
    },
    "airflow_dags": {
      "file": "$(basename "$DAGS_ARCHIVE" 2>/dev/null || echo '')",
      "sha256": "${DAGS_SHA}"
    }
  },
  "retention_policy": {
    "daily": 7,
    "weekly": 4,
    "monthly": 3
  }
}
JSONEOF

# ── 6. Upload vers R2 ─────────────────────────────────────────────────────────
R2_DEST="${BACKUP_ROOT}/${SCOPE}/${DATE}"
log "→ Upload vers R2 : ${R2_DEST}"

for f in "$WORK_DIR"/*; do
  [[ -f "$f" ]] || continue
  rclone copy "$f" "${R2_DEST}/" \
    --s3-no-check-bucket \
    --progress=false \
    2>>"$LOG_FILE"
  log "  ✓ $(basename "$f")"
done

# ── 7. Rotation GFS ───────────────────────────────────────────────────────────
log "→ Rotation (GFS)"

rotate_keep() {
  local prefix="$1" keep="$2"
  mapfile -t entries < <(rclone lsd "${BACKUP_ROOT}/${prefix}/" --format "p" 2>/dev/null | sort -r | tail -n +"$((keep + 1))")
  for old in "${entries[@]}"; do
    dir=$(echo "$old" | awk '{print $NF}')
    [[ -n "$dir" ]] && rclone purge "${BACKUP_ROOT}/${prefix}/${dir}" 2>>"$LOG_FILE" && log "  purge: ${prefix}/${dir}"
  done
}

case "$SCOPE" in
  daily)   rotate_keep daily   7 ;;
  weekly)  rotate_keep weekly  4 ;;
  monthly) rotate_keep monthly 3 ;;
esac

log "=== Backup $SCOPE terminé avec succès — $TIMESTAMP ==="
