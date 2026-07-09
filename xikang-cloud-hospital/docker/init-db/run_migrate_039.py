#!/usr/bin/env python3
"""Apply migrate_039_follow_up_pool_align_and_report.sql using xikang-cloud-hospital/.env DB profile."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
MIGRATION = ROOT / "docker" / "init-db" / "migrate_039_follow_up_pool_align_and_report.sql"


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


def resolve_db_config(env: dict[str, str]) -> dict[str, str | int]:
    profile = env.get("SPRING_PROFILES_ACTIVE") or env.get("spring.profiles.active") or "remote"
    if profile == "local":
        return {
            "host": env.get("DB_LOCAL_HOST", "localhost"),
            "port": int(env.get("DB_LOCAL_PORT", "3307")),
            "dbname": env.get("DB_LOCAL_NAME", "xikang_hospital"),
            "user": env.get("DB_LOCAL_USERNAME", "postgres"),
            "password": env.get("DB_LOCAL_PASSWORD", "postgres123"),
        }
    return {
        "host": env.get("DB_HOST", "localhost"),
        "port": int(env.get("DB_PORT", "5432")),
        "dbname": env.get("DB_NAME", "xikang_hospital"),
        "user": env.get("DB_USERNAME", "postgres"),
        "password": env.get("DB_PASSWORD") or env.get("DB_REMOTE_PASSWORD") or "changeme",
    }


def main() -> None:
    env = load_env()
    db = resolve_db_config(env)
    profile = env.get("SPRING_PROFILES_ACTIVE") or env.get("spring.profiles.active") or "remote"
    print(f"Profile: {profile}")
    print(f"Connecting to {db['user']}@{db['host']}:{db['port']}/{db['dbname']} ...")

    sql = MIGRATION.read_text(encoding="utf-8")
    conn = psycopg2.connect(**db)
    conn.autocommit = True
    cur = conn.cursor()
    print(f"=== Running {MIGRATION.name} ===")
    cur.execute(sql)
    print("OK")

    cur.execute("SELECT to_regclass('public.follow_up_visit_report')")
    table_name = cur.fetchone()[0]
    print(f"Verification: follow_up_visit_report => {table_name}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
