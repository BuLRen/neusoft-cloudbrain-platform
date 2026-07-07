#!/usr/bin/env python3
from pathlib import Path
import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
MIGRATION = "migrate_035_follow_up_demo_monitoring_assign.sql"


def load_env():
    values = {}
    if not ENV_PATH.exists():
        return values
    for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def main():
    env = load_env()
    profile = env.get("SPRING_PROFILES_ACTIVE", "local").strip().lower()
    if profile == "remote":
        conn = psycopg2.connect(
            host=env.get("DB_HOST"),
            port=int(env.get("DB_PORT", "5432")),
            dbname=env.get("DB_NAME"),
            user=env.get("DB_USERNAME"),
            password=env.get("DB_PASSWORD") or env.get("DB_REMOTE_PASSWORD"),
        )
    else:
        conn = psycopg2.connect(
            host=env.get("DB_LOCAL_HOST", "localhost"),
            port=int(env.get("DB_LOCAL_PORT", "3307")),
            dbname=env.get("DB_LOCAL_NAME"),
            user=env.get("DB_LOCAL_USERNAME"),
            password=env.get("DB_LOCAL_PASSWORD"),
        )
    sql = (Path(__file__).parent / MIGRATION).read_text(encoding="utf-8")
    with conn:
        with conn.cursor() as cur:
            cur.execute(sql)
    print(f"OK: {MIGRATION}")
    conn.close()


if __name__ == "__main__":
    main()
