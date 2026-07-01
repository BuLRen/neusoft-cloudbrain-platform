#!/usr/bin/env python3
"""Execute migrate_024 DDL only (new tables, no data changes)."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
SQL_PATH = Path(__file__).resolve().parent / "migrate_024_follow_up_demo_patient.sql"


def load_password() -> str:
    password = "changeme"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_REMOTE_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break
    return password


def main() -> None:
    sql = SQL_PATH.read_text(encoding="utf-8")
    conn = psycopg2.connect(
        host="43.139.102.203",
        port=5432,
        dbname="xikang_hospital",
        user="xikang_hospital",
        password=load_password(),
    )
    conn.autocommit = True
    cur = conn.cursor()
    print("Running migrate_024 (DDL only) ...")
    cur.execute(sql)
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'follow_up_last_visit_snapshot')"
    )
    print("follow_up_last_visit_snapshot:", cur.fetchone()[0])
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'follow_up_revisit_request')"
    )
    print("follow_up_revisit_request:", cur.fetchone()[0])
    cur.close()
    conn.close()
    print("DONE — next: python docker/init-db/seed_follow_up_demo_patient.py")


if __name__ == "__main__":
    main()
