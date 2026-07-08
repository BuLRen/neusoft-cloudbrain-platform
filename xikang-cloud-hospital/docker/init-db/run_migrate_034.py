#!/usr/bin/env python3
"""Execute migrate_034: follow-up history event types + monitoring transfer requests."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
DB_DIR = Path(__file__).resolve().parent
MIGRATION = "migrate_034_follow_up_monitoring_admin_assign.sql"


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
        "password": env.get("DB_LOCAL_PASSWORD", "postgres"),
    }


def main() -> None:
    env = load_env()
    conn_info = resolve_connection(env)
    sql = (DB_DIR / MIGRATION).read_text(encoding="utf-8")
    print(f"Connecting to {conn_info['host']}:{conn_info['port']}/{conn_info['dbname']} ...")
    with psycopg2.connect(**conn_info) as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
        conn.commit()
    print(f"OK: {MIGRATION}")


if __name__ == "__main__":
    main()
