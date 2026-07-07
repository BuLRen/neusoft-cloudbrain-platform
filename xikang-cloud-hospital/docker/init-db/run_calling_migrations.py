#!/usr/bin/env python3
"""Execute calling-system migrations (037/038) on PostgreSQL from .env credentials."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
MIGRATIONS_DIR = ROOT / "docker" / "migrations"

MIGRATIONS = [
    "037_employee_clinic_room.sql",
    "038_register_queue_position.sql",
]

CHECKS = [
    (
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = 'employee' AND column_name = 'clinic_room'",
        "employee.clinic_room column (expect 1)",
    ),
    (
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = 'register' AND column_name = 'queue_position'",
        "register.queue_position column (expect 1)",
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
    print(f"Connecting to {db['user']}@{db['host']}:{db['port']}/{db['dbname']} ...")

    conn = psycopg2.connect(**db)
    conn.autocommit = True
    cur = conn.cursor()

    for fname in MIGRATIONS:
        path = MIGRATIONS_DIR / fname
        sql = path.read_text(encoding="utf-8")
        print(f"=== Running {fname} ===")
        cur.execute(sql)
        print("OK")

    print("=== Verification ===")
    for query, label in CHECKS:
        cur.execute(query)
        print(f"{label}: {cur.fetchone()[0]}")

    cur.close()
    conn.close()
    print("DONE — 请重启 registration-service 后刷新「我的挂号」页面。")


if __name__ == "__main__":
    main()
