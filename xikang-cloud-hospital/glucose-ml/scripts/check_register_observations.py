#!/usr/bin/env python3
from pathlib import Path
import psycopg2

ROOT = Path(__file__).resolve().parents[1]
ENV_PATH = ROOT.parent / ".env"
password = "changeme"
if ENV_PATH.exists():
    for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        if line.startswith("DB_REMOTE_PASSWORD="):
            password = line.split("=", 1)[1].strip()
            break

conn = psycopg2.connect(
    host="43.139.102.203", port=5432, dbname="xikang_hospital",
    user="xikang_hospital", password=password,
)
cur = conn.cursor()
cur.execute(
    "SELECT COUNT(*) FROM patient_health_observation "
    "WHERE register_id = 3 AND metric_code = 'blood_glucose'"
)
print("blood_glucose rows:", cur.fetchone()[0])
cur.execute(
    "SELECT COUNT(DISTINCT observed_at) FROM patient_health_observation WHERE register_id = 3"
)
print("distinct hours:", cur.fetchone()[0])
cur.close()
conn.close()
