#!/usr/bin/env bash
# ==============================================================================
# IMF Pipeline — Restore depuis Cloudflare R2
# Usage : ./restore.sh <date> [scope]
# Exemple : ./restore.sh 2026-06-15 daily
# ==============================================================================
set -euo pipefail

TARGET_DATE="${1:?Usage: $0 <date YYYY-MM-DD> [daily|weekly|monthly]}"
SCOPE="${2:-daily}"
BUCKET="r2-imf:imf-ml"
BACKUP_PATH="${BUCKET}/backups/${SCOPE}/${TARGET_DATE}"
RESTORE_DIR=$(mktemp -d /tmp/imf-restore-XXXXXX)

log() { echo "[$(date +%H:%M:%S)] $*"; }
cleanup() { rm -rf "$RESTORE_DIR"; }
trap cleanup EXIT

log "=== Restore IMF depuis R2 — $TARGET_DATE ($SCOPE) ==="

# Vérification existence
if ! rclone ls "$BACKUP_PATH" > /dev/null 2>&1; then
  echo "ERREUR : backup introuvable dans $BACKUP_PATH"
  echo "Backups disponibles :"
  rclone lsd "${BUCKET}/backups/${SCOPE}/"
  exit 1
fi

# Téléchargement
log "→ Téléchargement depuis R2"
rclone copy "$BACKUP_PATH" "$RESTORE_DIR/"
log "  Fichiers téléchargés :"
ls -lh "$RESTORE_DIR/"

# Vérification manifeste
MANIFEST="${RESTORE_DIR}/manifest.json"
if [[ -f "$MANIFEST" ]]; then
  log "→ Vérification checksums"
  python3 << PYEOF
import json, hashlib, sys, os
manifest = json.load(open("${MANIFEST}"))
restore_dir = "${RESTORE_DIR}"
ok = True
for name, info in manifest["artefacts"].items():
    fname = info.get("file","")
    expected = info.get("sha256","")
    if not fname or not expected or expected in ("absent",""):
        continue
    fpath = os.path.join(restore_dir, fname)
    if not os.path.exists(fpath):
        print(f"  ⚠ {name}: fichier absent {fname}")
        continue
    h = hashlib.sha256(open(fpath,"rb").read()).hexdigest()
    status = "✓" if h == expected else "✗ MISMATCH"
    print(f"  {status} {name}: {fname}")
    if h != expected:
        ok = False
sys.exit(0 if ok else 1)
PYEOF
fi

# Restore PostgreSQL
PG_DUMP=$(ls "$RESTORE_DIR"/postgres_*.sql.gz 2>/dev/null | head -1 || true)
if [[ -n "$PG_DUMP" ]]; then
  log "→ Restore PostgreSQL"
  read -rp "  Confirmer restore PostgreSQL sur ${POSTGRES_HOST} ? (oui/non): " confirm
  if [[ "$confirm" == "oui" ]]; then
    PGPASSWORD="${POSTGRES_PASSWORD}" psql \
      -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT:-6543}" \
      -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
      < <(gunzip -c "$PG_DUMP") 2>&1 | tail -20
    log "  ✓ PostgreSQL restauré"
  else
    log "  Skipped PostgreSQL"
  fi
fi

# Restore ML models
ML_ARCHIVE=$(ls "$RESTORE_DIR"/ml_models_*.tar.gz 2>/dev/null | head -1 || true)
if [[ -n "$ML_ARCHIVE" ]]; then
  log "→ Restore modèles ML → /ml/models/"
  mkdir -p /ml
  tar -xzf "$ML_ARCHIVE" -C /ml
  log "  ✓ Modèles ML restaurés"
fi

log "=== Restore terminé depuis $TARGET_DATE ==="
