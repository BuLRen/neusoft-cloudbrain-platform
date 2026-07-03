#!/usr/bin/env python3
"""Execute migrate_029: follow-up monitoring, contact records, shift tables."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
DB_DIR = Path(__file__).resolve().parent
MIGRATION = "migrate_029_follow_up_monitoring_schedule_contact.sql"


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


def resolve_connection(env: dict[str, str]) -> dict[str, str | int]:
    profile = env.get("SPRING_PROFILES_ACTIVE", "local").strip().lower()
    if profile == "remote":
        return {
            "host": env.get("DB_HOST", "localhost"),
            "port": int(env.get("DB_PORT", "5432")),
            "dbname": env.get("DB_NAME", "xikang_hospital"),
            "user": env.get("DB_USERNAME", "xikang_hospital"),
            "password": env.get("DB_PASSWORD") or env.get("DB_REMOTE_PASSWORD") or "changeme",
        }
    return {
        "host": env.get("DB_LOCAL_HOST", "localhost"),
        "port": int(env.get("DB_LOCAL_PORT", "3307")),
        "dbname": env.get("DB_LOCAL_NAME", "xikang_hospital"),
        "user": env.get("DB_LOCAL_USERNAME", "postgres"),
        "password": env.get("DB_LOCAL_PASSWORD", "postgres123"),
    }


def main() -> None:
    env = load_env()
    params = resolve_connection(env)
    sql = (DB_DIR / MIGRATION).read_text(encoding="utf-8")
    print(f"Connecting to {params['host']}:{params['port']}/{params['dbname']} ...")
    conn = psycopg2.connect(**params)
    conn.autocommit = True
    cur = conn.cursor()
    print(f"Running {MIGRATION} ...")
    cur.execute(sql)
    cur.execute(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = 'public' AND table_name LIKE 'follow_up_shift%' ORDER BY 1"
    )
    tables = [row[0] for row in cur.fetchall()]
    print("Shift tables:", ", ".join(tables) if tables else "(none)")
    cur.close()
    conn.close()
    print("DONE — restart medtech-service if it was running during migration.")


if __name__ == "__main__":
    main()
