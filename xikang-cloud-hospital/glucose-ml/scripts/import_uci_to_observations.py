"""Import UCI hourly glucose series into patient_health_observation for demo register."""

from __future__ import annotations

import sys
from pathlib import Path

import pandas as pd
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.hourly import resample_to_hourly
from src.parsers.uci import events_from_uci, find_patient_files, parse_patient_file

ENV_PATH = ROOT.parent / ".env"
DEFAULT_REGISTER_ID = 3


def load_password() -> str:
    password = "changeme"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_REMOTE_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break
    return password


def align_to_recent(hourly: pd.DataFrame) -> pd.DataFrame:
    """UCI 原始时间为 1991 年；平移到「当前小时」为序列末端，便于前端展示与默认时间窗过滤。"""
    if hourly.empty:
        return hourly
    out = hourly.copy()
    last_ts = out["ts"].max()
    now = pd.Timestamp.now().floor("h")
    out["ts"] = out["ts"] + (now - last_ts)
    return out


def main() -> None:
    register_id = int(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REGISTER_ID
    files = find_patient_files()
    if not files:
        raise SystemExit("No UCI files. Run download_uci.py first.")
    hourly = align_to_recent(resample_to_hourly(events_from_uci(parse_patient_file(files[0]))))
    if len(hourly) < 48:
        raise SystemExit("Not enough hourly points")

    conn = psycopg2.connect(
        host="43.139.102.203",
        port=5432,
        dbname="xikang_hospital",
        user="xikang_hospital",
        password=load_password(),
    )
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute(
        "DELETE FROM patient_health_observation WHERE register_id = %s AND source_type = 'uci_import'",
        (register_id,),
    )
    cur.execute(
        "DELETE FROM follow_up_metric_forecast WHERE register_id = %s AND metric_code = 'blood_glucose'",
        (register_id,),
    )

    rows = 0
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

    from psycopg2.extras import execute_values

    execute_values(
        cur,
        """
        INSERT INTO patient_health_observation
            (register_id, observed_at, metric_code, metric_value, unit, source_type, note)
        VALUES %s
        ON CONFLICT (register_id, metric_code, observed_at) DO NOTHING
        """,
        [
            (r[0], r[1], r[2], r[3], r[4], "uci_import", "UCI AIM94 demo")
            for r in batch
        ],
        page_size=500,
    )
    rows = len(batch)

    # Link T2DM disease if medical_record exists
    cur.execute("SELECT id FROM disease WHERE disease_code = 'T2DM' LIMIT 1")
    dis = cur.fetchone()
    if dis:
        cur.execute("SELECT id FROM medical_record WHERE register_id = %s LIMIT 1", (register_id,))
        mr = cur.fetchone()
        if mr:
            cur.execute(
                """
                INSERT INTO medical_record_disease (medical_record_id, disease_id)
                SELECT %s, %s
                WHERE NOT EXISTS (
                    SELECT 1 FROM medical_record_disease WHERE medical_record_id = %s AND disease_id = %s
                )
                """,
                (mr[0], dis[0], mr[0], dis[0]),
            )

    print(f"Imported {rows} observations for register_id={register_id}")
    print(f"Time range: {hourly['ts'].min()} -> {hourly['ts'].max()} (aligned to recent)")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
