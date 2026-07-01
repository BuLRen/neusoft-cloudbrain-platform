#!/usr/bin/env python3
"""
三科随访护士 + 在管演示患者种子。
规则：优先 INSERT + NOT EXISTS；enrollment 使用 upsert 对齐 managing_department_id。
"""
from __future__ import annotations

import json
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"

NURSES = [
    {"username": "followup_nfm", "realname": "内分泌护士", "dept_id": 7},
    {"username": "followup_xx", "realname": "心血管护士", "dept_id": 3},
    {"username": "followup_hx", "realname": "呼吸护士", "dept_id": 2},
]

PATIENTS = [
    {
        "register_id": 9001,
        "patient_id": 9001,
        "username": "patient_tang",
        "real_name": "张糖友",
        "dept_id": 7,
        "case_number": "BL20260626001",
    },
    {
        "register_id": 9002,
        "patient_id": 9002,
        "username": "patient_xx",
        "real_name": "李心糖",
        "dept_id": 3,
        "case_number": "BL20260626002",
    },
    {
        "register_id": 9003,
        "patient_id": 9003,
        "username": "patient_hx",
        "real_name": "王呼糖",
        "dept_id": 2,
        "case_number": "BL20260626003",
    },
]

DEFAULT_PASSWORD = "followup123"
PATIENT_PASSWORD = "patient123"

LAST_VISIT_SNAPSHOTS = {
    9001: {
        "diagnosis_summary": "2型糖尿病 · 血糖控制一般，建议加强居家监测",
        "doctor_name": "内分泌科医师",
        "department_name": "内分泌科",
        "metrics": {
            "hba1c": {"value": 7.8, "unit": "%", "label": "糖化血红蛋白"},
            "fasting_glucose": {"value": 8.2, "unit": "mmol/L", "label": "空腹血糖"},
            "postprandial_glucose": {"value": 11.5, "unit": "mmol/L", "label": "餐后2h血糖"},
        },
    },
    9002: {
        "diagnosis_summary": "2型糖尿病合并冠心病 · 需关注血糖与心血管风险",
        "doctor_name": "心血管科医师",
        "department_name": "心血管科",
        "metrics": {
            "hba1c": {"value": 7.2, "unit": "%", "label": "糖化血红蛋白"},
            "fasting_glucose": {"value": 7.6, "unit": "mmol/L", "label": "空腹血糖"},
            "postprandial_glucose": {"value": 10.8, "unit": "mmol/L", "label": "餐后2h血糖"},
        },
    },
    9003: {
        "diagnosis_summary": "2型糖尿病 · 合并轻度慢阻肺，注意感染期血糖波动",
        "doctor_name": "呼吸科医师",
        "department_name": "呼吸科",
        "metrics": {
            "hba1c": {"value": 7.5, "unit": "%", "label": "糖化血红蛋白"},
            "fasting_glucose": {"value": 7.9, "unit": "mmol/L", "label": "空腹血糖"},
            "postprandial_glucose": {"value": 11.0, "unit": "mmol/L", "label": "餐后2h血糖"},
        },
    },
}


def load_env() -> dict[str, str]:
    env = {
        "host": "43.139.102.203",
        "port": "5432",
        "dbname": "xikang_hospital",
        "user": "xikang_hospital",
        "password": "changeme",
    }
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if "=" not in line or line.strip().startswith("#"):
                continue
            k, v = line.split("=", 1)
            k, v = k.strip(), v.strip()
            if k == "DB_HOST":
                env["host"] = v
            elif k == "DB_PORT":
                env["port"] = v
            elif k == "DB_NAME":
                env["dbname"] = v
            elif k == "DB_USERNAME":
                env["user"] = v
            elif k in ("DB_PASSWORD", "DB_REMOTE_PASSWORD"):
                env["password"] = v
    return env


def connect():
    e = load_env()
    return psycopg2.connect(
        host=e["host"],
        port=int(e["port"]),
        dbname=e["dbname"],
        user=e["user"],
        password=e["password"],
    )


def table_columns(cur, table: str) -> set[str]:
    cur.execute(
        """
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = %s
        """,
        (table,),
    )
    return {r[0] for r in cur.fetchall()}


def ensure_nurse(cur, username: str, realname: str, dept_id: int) -> int:
    cur.execute(
        "SELECT employee_id FROM users WHERE username = %s AND user_type = 7",
        (username,),
    )
    row = cur.fetchone()
    if row and row[0]:
        return row[0]

    cur.execute(
        "INSERT INTO employee (realname, deptment_id, password, delmark) VALUES (%s, %s, %s, 0) RETURNING id",
        (realname, dept_id, DEFAULT_PASSWORD),
    )
    employee_id = cur.fetchone()[0]
    cur.execute(
        """
        INSERT INTO users (username, password, real_name, user_type, employee_id, status, create_time, update_time)
        VALUES (%s, %s, %s, 7, %s, 1, NOW(), NOW())
        """,
        (username, DEFAULT_PASSWORD, realname, employee_id),
    )
    return employee_id


def ensure_patient_bundle(cur, p: dict, patient_cols: set[str]) -> None:
    rid, pid = p["register_id"], p["patient_id"]
    dept_id = p["dept_id"]

    patient_row = {
        "id": pid,
        "real_name": p["real_name"],
        "id_card": f"21019900101{pid:04d}",
        "gender": "男",
        "birthdate": "1990-01-01",
        "phone": f"1380013{pid:04d}",
        "allergy_history": "无",
        "delmark": 0,
    }
    if "relation" in patient_cols:
        patient_row["relation"] = "本人"
    if "is_primary" in patient_cols:
        patient_row["is_primary"] = 1
    patient_row = {k: v for k, v in patient_row.items() if k in patient_cols}

    cols = list(patient_row.keys())
    cur.execute(f"SELECT 1 FROM patient WHERE id = %s", (pid,))
    if not cur.fetchone():
        cur.execute(
            f"INSERT INTO patient ({', '.join(cols)}) VALUES ({', '.join(['%s'] * len(cols))})",
            tuple(patient_row[c] for c in cols),
        )

    cur.execute("SELECT id FROM employee WHERE deptment_id = %s AND delmark = 0 ORDER BY id LIMIT 1", (dept_id,))
    emp = cur.fetchone()
    employee_id = emp[0] if emp else 1

    cur.execute("SELECT id, visit_state, deptment_id, real_name FROM register WHERE id = %s", (rid,))
    reg = cur.fetchone()
    if not reg:
        cur.execute(
            """
            INSERT INTO register (
                id, case_number, real_name, gender, birthdate, age, age_type, home_address,
                visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
                is_book, regist_method, regist_money, visit_state, patient_id
            ) VALUES (
                %s, %s, %s, '男', '1990-01-01', 36, '年', '沈阳市演示区随访路 1 号',
                CURRENT_TIMESTAMP - INTERVAL '10 days', '上午', %s, %s, 2, 2,
                '否', '医保', 15.00, 3, %s
            )
            """,
            (rid, p["case_number"], p["real_name"], dept_id, employee_id, pid),
        )
    else:
        cur.execute(
            """
            UPDATE register SET
                case_number = %s,
                real_name = %s,
                deptment_id = %s,
                visit_state = 3,
                patient_id = %s,
                visit_date = COALESCE(visit_date, CURRENT_TIMESTAMP - INTERVAL '10 days')
            WHERE id = %s
            """,
            (p["case_number"], p["real_name"], dept_id, pid, rid),
        )

    cur.execute(
        """
        INSERT INTO follow_up_enrollment (
            register_id, managing_department_id, priority_level,
            interview_interval_days, observation_interval_days, status
        ) VALUES (%s, %s, 'high', 7, 1, 'active')
        ON CONFLICT (register_id) DO UPDATE SET
            managing_department_id = EXCLUDED.managing_department_id,
            status = 'active'
        """,
        (rid, dept_id),
    )

    cur.execute(
        """
        INSERT INTO follow_up_patient_profile (
            register_id, department_id, priority_level, interview_interval_days, observation_interval_days
        ) VALUES (%s, %s, 'high', 7, 1)
        ON CONFLICT (register_id) DO UPDATE SET department_id = EXCLUDED.department_id
        """,
        (rid, dept_id),
    )

    cur.execute(
        """
        INSERT INTO follow_up_communication_session (register_id, department_id, status, ai_escalation_enabled)
        SELECT %s, %s, 'active', 1
        WHERE NOT EXISTS (
            SELECT 1 FROM follow_up_communication_session WHERE register_id = %s
        )
        """,
        (rid, dept_id, rid),
    )

    cur.execute(
        """
        INSERT INTO users (username, password, real_name, user_type, patient_id, status, create_time, update_time)
        SELECT %s, %s, %s, 6, %s, 1, NOW(), NOW()
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = %s)
        """,
        (p["username"], PATIENT_PASSWORD, p["real_name"], pid, p["username"]),
    )
    cur.execute(
        "UPDATE users SET patient_id = %s, real_name = %s WHERE username = %s",
        (pid, p["real_name"], p["username"]),
    )

    cur.execute(
        """
        INSERT INTO user_patient_managed (user_id, patient_id, relation)
        SELECT u.id, %s, '本人'
        FROM users u
        WHERE u.username = %s
          AND NOT EXISTS (
            SELECT 1 FROM user_patient_managed upm
            WHERE upm.user_id = u.id AND upm.patient_id = %s
          )
        """,
        (pid, p["username"], pid),
    )

    if rid in (9002, 9003):
        cur.execute(
            """
            INSERT INTO patient_health_observation (
                register_id, observed_at, metric_code, metric_value, unit, source_type, note
            )
            SELECT %s, ts, 'blood_glucose', val, 'mmol/L', 'patient_report', '演示居家血糖'
            FROM (VALUES
                (CURRENT_TIMESTAMP - INTERVAL '2 days', 7.2),
                (CURRENT_TIMESTAMP - INTERVAL '1 day', 8.1),
                (CURRENT_TIMESTAMP - INTERVAL '6 hours', 6.8)
            ) AS t(ts, val)
            WHERE NOT EXISTS (
                SELECT 1 FROM patient_health_observation
                WHERE register_id = %s AND source_type = 'patient_report'
            )
            """,
            (rid, rid),
        )

    snap = LAST_VISIT_SNAPSHOTS.get(rid)
    if snap:
        cur.execute(
            """
            INSERT INTO follow_up_last_visit_snapshot (
                register_id, visit_date, diagnosis_summary, professional_metrics, doctor_name, department_name
            )
            VALUES (%s, (CURRENT_DATE - INTERVAL '14 days')::date, %s, %s::jsonb, %s, %s)
            ON CONFLICT (register_id) DO UPDATE SET
                visit_date = EXCLUDED.visit_date,
                diagnosis_summary = EXCLUDED.diagnosis_summary,
                professional_metrics = EXCLUDED.professional_metrics,
                doctor_name = EXCLUDED.doctor_name,
                department_name = EXCLUDED.department_name
            """,
            (
                rid,
                snap["diagnosis_summary"],
                json.dumps(snap["metrics"], ensure_ascii=False),
                snap["doctor_name"],
                snap["department_name"],
            ),
        )


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute(
        """
        INSERT INTO disease (disease_code, disease_name, diseaseicd, disease_category)
        SELECT 'T2DM', '2型糖尿病', 'E11.9', '代谢内分泌疾病'
        WHERE NOT EXISTS (SELECT 1 FROM disease WHERE disease_code = 'T2DM')
        """
    )

    patient_cols = table_columns(cur, "patient")

    print("创建随访护士账号...")
    for n in NURSES:
        eid = ensure_nurse(cur, n["username"], n["realname"], n["dept_id"])
        print(f"  {n['username']} / {DEFAULT_PASSWORD}  科室={n['dept_id']}  employee_id={eid}")

    print("创建/对齐在管演示患者...")
    for p in PATIENTS:
        ensure_patient_bundle(cur, p, patient_cols)
        print(f"  register {p['register_id']} {p['real_name']} 科室={p['dept_id']}  账号 {p['username']}/{PATIENT_PASSWORD}")

    print("完成。护士登录后仅可访问随访系统；患者可在随访管理录入血糖并与护士沟通。")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
