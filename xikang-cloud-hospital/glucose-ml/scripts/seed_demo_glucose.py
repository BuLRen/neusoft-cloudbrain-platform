"""Seed last-14-day UCI glucose baseline for demo register (source_type=uci_import)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import pandas as pd
import psycopg2
from psycopg2.extras import execute_values

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.hourly import resample_to_hourly
from src.parsers.uci import events_from_uci, find_patient_files, parse_patient_file

ENV_PATH = ROOT.parent / ".env"
DEFAULT_REGISTER_ID = 9001
DAYS = 14


def load_password() -> str:
    password = "changeme"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_REMOTE_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break
    return password


def align_to_recent(hourly: pd.DataFrame) -> pd.DataFrame:
    if hourly.empty:
        return hourly
    out = hourly.copy()
    last_ts = out["ts"].max()
    now = pd.Timestamp.now().floor("h")
    out["ts"] = out["ts"] + (now - last_ts)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="Import recent UCI glucose slice for demo patient")
    parser.add_argument("--register-id", type=int, default=DEFAULT_REGISTER_ID)
    parser.add_argument("--days", type=int, default=DAYS)
    args = parser.parse_args()
    register_id = args.register_id

    files = find_patient_files()
    if not files:
        raise SystemExit("No UCI files. Run download_uci.py first.")

    hourly = align_to_recent(resample_to_hourly(events_from_uci(parse_patient_file(files[0]))))
    if hourly.empty:
        raise SystemExit("No hourly glucose data")

    cutoff = hourly["ts"].max() - pd.Timedelta(days=args.days)
    hourly = hourly[hourly["ts"] >= cutoff].copy()
    if len(hourly) < 24:
        raise SystemExit(f"Not enough hourly points in last {args.days} days")

    conn = psycopg2.connect(
        host="43.139.102.203",
        port=5432,
        dbname="xikang_hospital",
        user="xikang_hospital",
        password=load_password(),
    )
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute("SELECT id, real_name FROM register WHERE id = %s", (register_id,))
    reg = cur.fetchone()
    if not reg:
        raise SystemExit(
            f"register_id={register_id} 不存在。请先执行：\n"
            f"  1. python docker/init-db/run_migrate_024_demo_patient.py  （建表）\n"
            f"  2. python docker/init-db/seed_follow_up_demo_patient.py   （演示患者数据）\n"
            f"然后再运行本脚本。"
        )

    cur.execute(
        "DELETE FROM patient_health_observation WHERE register_id = %s AND source_type = 'uci_import'",
        (register_id,),
    )
    cur.execute(
        "DELETE FROM follow_up_metric_forecast WHERE register_id = %s AND metric_code = 'blood_glucose'",
        (register_id,),
    )

    batch: list[tuple] = []
    for _, row in hourly.iterrows():
        ts = row["ts"].to_pydatetime()
        for metric, val in [
            ("blood_glucose", row["blood_glucose"]),
            ("insulin_total", row["insulin_total"]),
            ("meal_flag", row["meal_flag"]),
            ("exercise_flag", row["exercise_flag"]),
        ]:
            batch.append(
                (register_id, ts, metric, float(val), "mmol/L" if metric == "blood_glucose" else "1")
            )

    execute_values(
        cur,
        """
        INSERT INTO patient_health_observation
            (register_id, observed_at, metric_code, metric_value, unit, source_type, note)
        VALUES %s
        ON CONFLICT (register_id, metric_code, observed_at) DO NOTHING
        """,
        [
            (r[0], r[1], r[2], r[3], r[4], "uci_import", "UCI demo baseline (last 14d)")
            for r in batch
        ],
        page_size=500,
    )

    print(f"Imported {len(batch)} uci_import observations for register_id={register_id}")
    print(f"Time range: {hourly['ts'].min()} -> {hourly['ts'].max()}")
    print("Next: patient can add source_type=patient_report entries via patient portal API.")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
