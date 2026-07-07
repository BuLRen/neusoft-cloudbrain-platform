#!/usr/bin/env python3
"""Execute follow-up production migrations on remote PostgreSQL."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
INIT_DB = ROOT / "docker" / "init-db"

MIGRATIONS = [
    "migrate_020_follow_up_production.sql",
    "migrate_021_remove_follow_up_demo_seeds.sql",
    "migrate_036_follow_up_clinical_backfill.sql",
    "migrate_037_communication_read_cursor.sql",
    "migrate_038_follow_up_pending_schedule.sql",
]

CHECKS = [
    ("SELECT COUNT(*) FROM follow_up_enrollment", "follow_up_enrollment rows"),
    ("SELECT COUNT(*) FROM patient_health_observation", "patient_health_observation rows"),
    ("SELECT COUNT(*) FROM follow_up_health_metric", "follow_up_health_metric rows (expect 0)"),
    (
        "SELECT COUNT(*) FROM follow_up_patient_profile WHERE register_id BETWEEN 1001 AND 1005",
        "demo profiles 1001-1005 (expect 0)",
    ),
    (
        "SELECT COUNT(*) FROM register r WHERE r.visit_state = 3 AND EXISTS ("
        "SELECT 1 FROM medical_record mr "
        "INNER JOIN medical_record_disease mrd ON mrd.medical_record_id = mr.id "
        "WHERE mr.register_id = r.id)",
        "eligible patients (visit ended + diagnosis)",
    ),
]


def load_env() -> dict[str, str]:
    values: dict[str, str] = {}
    if not ENV_PATH.exists():
        return values
    for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def load_password(env: dict[str, str]) -> str:
    return env.get("DB_PASSWORD") or env.get("DB_REMOTE_PASSWORD") or "changeme"


def main() -> None:
    env = load_env()
    conn = psycopg2.connect(
        host=env.get("DB_HOST", "43.139.102.203"),
        port=int(env.get("DB_PORT", "5432")),
        dbname=env.get("DB_NAME", "xikang_hospital"),
        user=env.get("DB_USERNAME", "xikang_hospital"),
        password=load_password(env),
    )
    conn.autocommit = True
    cur = conn.cursor()

    for fname in MIGRATIONS:
        sql = (INIT_DB / fname).read_text(encoding="utf-8")
        print(f"=== Running {fname} ===")
        cur.execute(sql)
        print("OK")

    print("=== Verification ===")
    for query, label in CHECKS:
        cur.execute(query)
        print(f"{label}: {cur.fetchone()[0]}")

    cur.close()
    conn.close()
    print("DONE")


if __name__ == "__main__":
    main()
