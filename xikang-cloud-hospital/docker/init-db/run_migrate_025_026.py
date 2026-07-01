#!/usr/bin/env python3
"""Execute migrate_025 + migrate_026 DDL."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
DB_DIR = Path(__file__).resolve().parent


def load_password() -> str:
    password = "changeme"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_REMOTE_PASSWORD=") or line.startswith("DB_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break
    return password


def load_host() -> str:
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_HOST="):
                return line.split("=", 1)[1].strip()
    return "43.139.102.203"


def main() -> None:
    conn = psycopg2.connect(
        host=load_host(),
        port=5432,
        dbname="xikang_hospital",
        user="xikang_hospital",
        password=load_password(),
    )
    conn.autocommit = True
    cur = conn.cursor()
    for name in ("migrate_025_follow_up_staff_role.sql", "migrate_026_follow_up_metric_forecast.sql"):
        sql = (DB_DIR / name).read_text(encoding="utf-8")
        print(f"Running {name} ...")
        cur.execute(sql)
    cur.close()
    conn.close()
    print("DONE — next: python docker/init-db/seed_follow_up_nurses.py")


if __name__ == "__main__":
    main()
