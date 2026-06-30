#!/usr/bin/env python3
"""Execute migrate_022 on remote PostgreSQL."""
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"
SQL_PATH = Path(__file__).resolve().parent / "migrate_022_glucose_forecast.sql"


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
    print("Running migrate_022_glucose_forecast.sql ...")
    cur.execute(sql)
    cur.execute("SELECT COUNT(*) FROM follow_up_metric_forecast")
    print("follow_up_metric_forecast rows:", cur.fetchone()[0])
    cur.execute("SELECT disease_code FROM disease WHERE disease_code IN ('T2DM','T1DM')")
    print("diseases:", cur.fetchall())
    cur.close()
    conn.close()
    print("DONE")


if __name__ == "__main__":
    main()
