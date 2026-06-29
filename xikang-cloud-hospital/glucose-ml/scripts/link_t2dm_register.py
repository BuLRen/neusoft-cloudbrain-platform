#!/usr/bin/env python3
"""Link T2DM disease to register's medical_record if missing."""
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

register_id = 3
conn = psycopg2.connect(
    host="43.139.102.203", port=5432, dbname="xikang_hospital",
    user="xikang_hospital", password=password,
)
conn.autocommit = True
cur = conn.cursor()
cur.execute("SELECT id FROM disease WHERE disease_code = 'T2DM' LIMIT 1")
dis = cur.fetchone()
cur.execute("SELECT id FROM medical_record WHERE register_id = %s LIMIT 1", (register_id,))
mr = cur.fetchone()
if dis and mr:
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
    print("linked T2DM:", cur.rowcount)
else:
    print("skip: disease or medical_record missing", dis, mr)
cur.close()
conn.close()
