"""
DAG : dag_backup
Backup enterprise vers Cloudflare R2 avec rotation GFS.

Schedule :
  - daily  : tous les jours à 03h00
  - weekly : dimanche à 03h30
  - monthly: 1er du mois à 04h00

Artefacts sauvegardés :
  1. Dump PostgreSQL (schémas app · ml · dw) compressé gzip
  2. Modèles ML (champion + challenger) en .tar.gz
  3. Snapshot Redis (dump.rdb)
  4. DAGs Airflow

Retention : 7 quotidiens · 4 hebdomadaires · 3 mensuels
Chaque backup produit un manifeste JSON avec SHA-256 par fichier.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import subprocess
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

import boto3
from botocore.config import Config as BotoConfig

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule

# ── Config R2 ──────────────────────────────────────────────────────────────────
R2_ENDPOINT    = "https://61a23f65159306f2aaf08c0e0bf76d59.r2.cloudflarestorage.com"
R2_ACCESS_KEY  = os.environ.get("R2_ACCESS_KEY_ID", "6702e95fdff644802dc657728977f54e")
R2_SECRET_KEY  = os.environ.get("R2_SECRET_ACCESS_KEY", "40caa6eb64cf35728bf69fe56f48d3de109a835dc8294e609734e7d517b8b966")
R2_BUCKET      = "imf-ml"
BACKUP_PREFIX  = "backups"

# ── PostgreSQL ─────────────────────────────────────────────────────────────────
PG_HOST = os.environ.get("POSTGRES_HOST", "aws-0-eu-west-3.pooler.supabase.com")
PG_PORT = os.environ.get("POSTGRES_PORT", "6543")
PG_USER = os.environ.get("POSTGRES_USER", "postgres.ceiqkvvacjsakycsgcfz")
PG_PASS = os.environ.get("POSTGRES_PASSWORD", "")
PG_DB   = os.environ.get("POSTGRES_DB", "postgres")

# ── Retention GFS ──────────────────────────────────────────────────────────────
RETENTION = {"daily": 7, "weekly": 4, "monthly": 3}

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 2,
    "retry_delay": timedelta(minutes=10),
    "email_on_failure": False,
}


def _r2_client():
    return boto3.client(
        "s3",
        endpoint_url=R2_ENDPOINT,
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=BotoConfig(signature_version="s3v4"),
        region_name="auto",
    )


def _sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _upload(client, local_path: str, r2_key: str) -> str:
    client.upload_file(local_path, R2_BUCKET, r2_key)
    return r2_key


# ── Tâche 1 : dump PostgreSQL ──────────────────────────────────────────────────
def dump_postgres(scope: str, **ctx) -> dict:
    ts    = datetime.utcnow().strftime("%Y%m%dT%H%M%S")
    date  = datetime.utcnow().strftime("%Y-%m-%d")
    fname = f"postgres_{scope}_{ts}.sql.gz"

    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, fname)
        env = {**os.environ, "PGPASSWORD": PG_PASS}
        proc = subprocess.run(
            [
                "pg_dump",
                f"--host={PG_HOST}", f"--port={PG_PORT}",
                f"--username={PG_USER}", f"--dbname={PG_DB}",
                "--schema=app", "--schema=ml", "--schema=dw",
                "--clean", "--if-exists", "--no-password",
            ],
            capture_output=True, env=env, check=True,
        )
        with gzip.open(out, "wb", compresslevel=9) as gz:
            gz.write(proc.stdout)

        sha = _sha256(out)
        size = os.path.getsize(out)
        key = f"{BACKUP_PREFIX}/{scope}/{date}/{fname}"
        _upload(_r2_client(), out, key)

    print(f"[backup] PostgreSQL → r2://{R2_BUCKET}/{key} ({size//1024}KB, sha256:{sha[:16]}…)")
    return {"file": fname, "key": key, "sha256": sha, "size": size}


# ── Tâche 2 : archive modèles ML ──────────────────────────────────────────────
def backup_ml_models(scope: str, **ctx) -> dict:
    ts   = datetime.utcnow().strftime("%Y%m%dT%H%M%S")
    date = datetime.utcnow().strftime("%Y-%m-%d")
    fname = f"ml_models_{scope}_{ts}.tar.gz"

    ml_dir = Path(os.environ.get("MCRS_MODEL_DIR", "/ml/models/mcrs")).parent

    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, fname)
        if ml_dir.exists():
            subprocess.run(["tar", "-czf", out, "-C", str(ml_dir.parent), ml_dir.name], check=True)
        else:
            # Models dans le container ml-api
            subprocess.run(["docker", "cp", "imf-ml-api:/ml/models", f"{tmp}/models"], check=True)
            subprocess.run(["tar", "-czf", out, "-C", tmp, "models"], check=True)

        sha  = _sha256(out)
        size = os.path.getsize(out)
        key  = f"{BACKUP_PREFIX}/{scope}/{date}/{fname}"
        _upload(_r2_client(), out, key)

    print(f"[backup] ML models → r2://{R2_BUCKET}/{key} ({size//1024}KB)")
    return {"file": fname, "key": key, "sha256": sha, "size": size}


# ── Tâche 3 : snapshot Redis ───────────────────────────────────────────────────
def backup_redis(scope: str, **ctx) -> dict:
    ts   = datetime.utcnow().strftime("%Y%m%dT%H%M%S")
    date = datetime.utcnow().strftime("%Y-%m-%d")
    fname = f"redis_{scope}_{ts}.rdb.gz"

    redis_pass = os.environ.get("REDIS_PASSWORD", "staging_redis_pass")

    with tempfile.TemporaryDirectory() as tmp:
        # Forcer un save Redis
        subprocess.run(
            ["docker", "exec", "imf_staging_redis",
             "redis-cli", "-a", redis_pass, "--no-auth-warning", "BGSAVE"],
            capture_output=True,
        )
        import time; time.sleep(3)

        rdb_tmp = os.path.join(tmp, "dump.rdb")
        subprocess.run(["docker", "cp", "imf_staging_redis:/data/dump.rdb", rdb_tmp], check=True)

        out = os.path.join(tmp, fname)
        with open(rdb_tmp, "rb") as fi, gzip.open(out, "wb", compresslevel=9) as gz:
            gz.write(fi.read())

        sha  = _sha256(out)
        size = os.path.getsize(out)
        key  = f"{BACKUP_PREFIX}/{scope}/{date}/{fname}"
        _upload(_r2_client(), out, key)

    print(f"[backup] Redis → r2://{R2_BUCKET}/{key} ({size//1024}KB)")
    return {"file": fname, "key": key, "sha256": sha, "size": size}


# ── Tâche 4 : manifest + upload ───────────────────────────────────────────────
def build_manifest(scope: str, **ctx) -> None:
    ti   = ctx["ti"]
    date = datetime.utcnow().strftime("%Y-%m-%d")
    ts   = datetime.utcnow().isoformat()

    manifest = {
        "backup_id":  datetime.utcnow().strftime("%Y%m%dT%H%M%S"),
        "scope":      scope,
        "date":       date,
        "generated":  ts,
        "bucket":     R2_BUCKET,
        "retention":  RETENTION,
        "artefacts": {
            "postgres":    ti.xcom_pull(task_ids="dump_postgres"),
            "ml_models":   ti.xcom_pull(task_ids="backup_ml_models"),
            "redis":       ti.xcom_pull(task_ids="backup_redis"),
        },
    }

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(manifest, f, indent=2)
        tmp_path = f.name

    key = f"{BACKUP_PREFIX}/{scope}/{date}/manifest.json"
    _upload(_r2_client(), tmp_path, key)
    os.unlink(tmp_path)
    print(f"[backup] Manifeste → r2://{R2_BUCKET}/{key}")
    print(json.dumps(manifest, indent=2))


# ── Tâche 5 : rotation GFS ────────────────────────────────────────────────────
def rotate_old_backups(scope: str, **ctx) -> None:
    keep  = RETENTION[scope]
    r2    = _r2_client()
    prefix = f"{BACKUP_PREFIX}/{scope}/"

    resp = r2.list_objects_v2(Bucket=R2_BUCKET, Prefix=prefix, Delimiter="/")
    dirs = sorted(
        [cp["Prefix"].rstrip("/").split("/")[-1] for cp in resp.get("CommonPrefixes", [])],
        reverse=True,
    )

    to_delete = dirs[keep:]
    for old_date in to_delete:
        old_prefix = f"{BACKUP_PREFIX}/{scope}/{old_date}/"
        paginator = r2.get_paginator("list_objects_v2")
        for page in paginator.paginate(Bucket=R2_BUCKET, Prefix=old_prefix):
            objects = [{"Key": o["Key"]} for o in page.get("Contents", [])]
            if objects:
                r2.delete_objects(Bucket=R2_BUCKET, Delete={"Objects": objects})
        print(f"[rotation] Purgé {old_prefix} ({len(objects)} objets)")


# ── DAG daily (03h00) ─────────────────────────────────────────────────────────
def _make_dag(dag_id: str, schedule: str, scope: str) -> DAG:
    with DAG(
        dag_id=dag_id,
        description=f"Backup IMF → R2 ({scope})",
        schedule_interval=schedule,
        start_date=datetime(2026, 1, 1),
        catchup=False,
        default_args=DEFAULT_ARGS,
        tags=["backup", "r2", scope],
    ) as dag:

        t_pg = PythonOperator(
            task_id="dump_postgres",
            python_callable=dump_postgres,
            op_kwargs={"scope": scope},
        )
        t_ml = PythonOperator(
            task_id="backup_ml_models",
            python_callable=backup_ml_models,
            op_kwargs={"scope": scope},
        )
        t_rd = PythonOperator(
            task_id="backup_redis",
            python_callable=backup_redis,
            op_kwargs={"scope": scope},
        )
        t_mf = PythonOperator(
            task_id="build_manifest",
            python_callable=build_manifest,
            op_kwargs={"scope": scope},
            trigger_rule=TriggerRule.ALL_SUCCESS,
        )
        t_rot = PythonOperator(
            task_id="rotate_old_backups",
            python_callable=rotate_old_backups,
            op_kwargs={"scope": scope},
        )

        [t_pg, t_ml, t_rd] >> t_mf >> t_rot
    return dag


dag_backup_daily   = _make_dag("dag_backup_daily",   "0 3 * * *",   "daily")
dag_backup_weekly  = _make_dag("dag_backup_weekly",  "30 3 * * 0",  "weekly")
dag_backup_monthly = _make_dag("dag_backup_monthly", "0 4 1 * *",   "monthly")
