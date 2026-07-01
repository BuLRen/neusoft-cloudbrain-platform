#!/usr/bin/env python3
"""
演示患者「张糖友」种子数据（register_id=9001）。
规则：仅 INSERT 新行，不 UPDATE/DELETE 原有数据（可重复执行，已存在则跳过）。
前置：先执行 migrate_024（建表）或 run_migrate_024_demo_patient.py
"""
from __future__ import annotations

import json
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = ROOT / ".env"

DEMO_REGISTER_ID = 9001
DEMO_PATIENT_ID = 9001
DEMO_MEDICAL_RECORD_ID = 59001
DEMO_USERNAME = "patient_tang"

PROFESSIONAL_METRICS = {
    "hba1c": {"value": 7.8, "unit": "%", "label": "糖化血红蛋白"},
    "fasting_glucose": {"value": 8.2, "unit": "mmol/L", "label": "空腹血糖"},
    "postprandial_glucose": {"value": 11.5, "unit": "mmol/L", "label": "餐后2h血糖"},
}


def load_password() -> str:
    password = "changeme"
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("DB_REMOTE_PASSWORD="):
                password = line.split("=", 1)[1].strip()
                break
    return password


def connect():
    return psycopg2.connect(
        host="43.139.102.203",
        port=5432,
        dbname="xikang_hospital",
        user="xikang_hospital",
        password=load_password(),
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


def insert_if_absent(cur, table: str, key_col: str, key_val, columns: dict) -> bool:
    cur.execute(f"SELECT 1 FROM {table} WHERE {key_col} = %s LIMIT 1", (key_val,))
    if cur.fetchone():
        return False
    cols = list(columns.keys())
    placeholders = ", ".join(["%s"] * len(cols))
    col_sql = ", ".join(cols)
    cur.execute(
        f"INSERT INTO {table} ({col_sql}) VALUES ({placeholders})",
        tuple(columns[c] for c in cols),
    )
    return True


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    patient_cols = table_columns(cur, "patient")

    patient_row = {
        "id": DEMO_PATIENT_ID,
        "real_name": "张糖友",
        "id_card": "210198506151234",
        "gender": "男",
        "birthdate": "1985-06-15",
        "phone": "13800138888",
        "allergy_history": "无",
        "delmark": 0,
    }
    if "relation" in patient_cols:
        patient_row["relation"] = "本人"
    if "is_primary" in patient_cols:
        patient_row["is_primary"] = 1
    patient_row = {k: v for k, v in patient_row.items() if k in patient_cols}
    insert_if_absent(cur, "patient", "id", DEMO_PATIENT_ID, patient_row)

    cur.execute(
        """
        INSERT INTO disease (disease_code, disease_name, diseaseicd, disease_category)
        SELECT 'T2DM', '2型糖尿病', 'E11.9', '代谢内分泌疾病'
        WHERE NOT EXISTS (SELECT 1 FROM disease WHERE disease_code = 'T2DM')
        """
    )

    cur.execute(
        """
        SELECT id FROM employee WHERE deptment_id = 7 AND delmark = 0 ORDER BY id LIMIT 1
        """
    )
    emp = cur.fetchone()
    employee_id = emp[0] if emp else 1

    cur.execute("SELECT 1 FROM register WHERE id = %s", (DEMO_REGISTER_ID,))
    if not cur.fetchone():
        cur.execute(
            """
            INSERT INTO register (
                id, case_number, real_name, gender, birthdate, age, age_type, home_address,
                visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
                is_book, regist_method, regist_money, visit_state, patient_id
            ) VALUES (
                %s, 'BL20260626001', '张糖友', '男', '1985-06-15', 41, '年', '沈阳市和平区演示路 88 号',
                CURRENT_TIMESTAMP - INTERVAL '14 days', '上午', 7, %s, 2, 2,
                '否', '医保', 15.00, 3, %s
            )
            """,
            (DEMO_REGISTER_ID, employee_id, DEMO_PATIENT_ID),
        )

    mr_cols = table_columns(cur, "medical_record")
    mr_row: dict = {"id": DEMO_MEDICAL_RECORD_ID, "register_id": DEMO_REGISTER_ID}
    if "chief_complaint" in mr_cols:
        mr_row.update(
            {
                "chief_complaint": "血糖控制不佳复诊",
                "present_illness": "既往 2 型糖尿病 5 年，近期空腹血糖波动偏大。",
                "treatment_proposal": "继续二甲双胍，加强血糖监测与生活方式干预。",
                "diagnosis": "2型糖尿病",
                "preliminary_diagnosis": "2型糖尿病",
            }
        )
    else:
        mr_row.update(
            {
                "readme": "血糖控制不佳复诊",
                "present": "既往 2 型糖尿病 5 年，近期空腹血糖波动偏大。",
                "proposal": "继续二甲双胍，加强血糖监测与生活方式干预。",
                "diagnosis": "2型糖尿病",
                "preliminary_diagnosis": "2型糖尿病",
            }
        )
    mr_row = {k: v for k, v in mr_row.items() if k in mr_cols}
    insert_if_absent(cur, "medical_record", "id", DEMO_MEDICAL_RECORD_ID, mr_row)

    cur.execute(
        """
        INSERT INTO medical_record_disease (medical_record_id, disease_id)
        SELECT %s, d.id FROM disease d
        WHERE d.disease_code = 'T2DM'
          AND NOT EXISTS (
            SELECT 1 FROM medical_record_disease mrd
            WHERE mrd.medical_record_id = %s AND mrd.disease_id = d.id
          )
        LIMIT 1
        """,
        (DEMO_MEDICAL_RECORD_ID, DEMO_MEDICAL_RECORD_ID),
    )

    for sql in (
        """
        INSERT INTO follow_up_patient_profile (
            register_id, department_id, priority_level, interview_interval_days, observation_interval_days
        )
        SELECT 9001, 7, 'high', 7, 1
        WHERE NOT EXISTS (SELECT 1 FROM follow_up_patient_profile WHERE register_id = 9001)
        """,
        """
        INSERT INTO follow_up_enrollment (
            register_id, managing_department_id, priority_level,
            interview_interval_days, observation_interval_days, status
        )
        SELECT 9001, 7, 'high', 7, 1, 'active'
        WHERE NOT EXISTS (SELECT 1 FROM follow_up_enrollment WHERE register_id = 9001)
        """,
        """
        INSERT INTO follow_up_last_visit_snapshot (
            register_id, visit_date, diagnosis_summary, professional_metrics, doctor_name, department_name
        )
        SELECT
            9001,
            (CURRENT_DATE - INTERVAL '14 days')::date,
            '2型糖尿病 · 血糖控制一般，建议加强居家监测',
            %s::jsonb,
            '内分泌科医师',
            '内分泌科'
        WHERE NOT EXISTS (SELECT 1 FROM follow_up_last_visit_snapshot WHERE register_id = 9001)
        """,
    ):
        if "professional_metrics" in sql:
            cur.execute(sql, (json.dumps(PROFESSIONAL_METRICS, ensure_ascii=False),))
        else:
            cur.execute(sql)

    for plan in (
        (91001, 7, "7 days", "medication", "completed", "按时服用二甲双胍，监测空腹血糖"),
        (91002, 14, "0 days", "revisit", "pending", "建议两周后内分泌科复诊，携带居家血糖记录"),
    ):
        cur.execute(
            """
            INSERT INTO ai_follow_up_plan (
                id, register_id, follow_up_day, planned_date, follow_up_type, plan_status, content_template
            )
            SELECT %s, 9001, %s, CURRENT_DATE - (%s)::interval, %s, %s, %s
            WHERE NOT EXISTS (SELECT 1 FROM ai_follow_up_plan WHERE id = %s)
            """,
            (plan[0], plan[1], plan[2], plan[3], plan[4], plan[5], plan[0]),
        )

    cur.execute(
        """
        INSERT INTO prescription (register_id, drug_id, drug_usage, drug_number, creation_time)
        SELECT 9001, d.id, '口服，每日2次，每次0.5g，餐中服用', '0.5g×60片', CURRENT_TIMESTAMP - INTERVAL '14 days'
        FROM drug_info d
        WHERE d.drug_name LIKE '%%二甲双胍%%'
          AND NOT EXISTS (SELECT 1 FROM prescription WHERE register_id = 9001)
        LIMIT 1
        """
    )

    cur.execute(
        """
        INSERT INTO users (username, password, real_name, user_type, patient_id, status)
        SELECT %s, 'patient123', '张糖友', 6, 9001, 1
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = %s)
        """,
        (DEMO_USERNAME, DEMO_USERNAME),
    )

    cur.execute(
        """
        INSERT INTO user_patient_managed (user_id, patient_id, relation)
        SELECT u.id, 9001, '本人'
        FROM users u
        WHERE u.username = %s
          AND NOT EXISTS (
            SELECT 1 FROM user_patient_managed upm
            WHERE upm.user_id = u.id AND upm.patient_id = 9001
          )
        """,
        (DEMO_USERNAME,),
    )

    cur.execute("SELECT id, real_name, patient_id FROM register WHERE id = 9001")
    reg = cur.fetchone()

    print("演示患者种子完成（仅新增，不修改/删除原有行）")
    print("register 9001:", reg)
    print("账号: patient_tang / patient123")
    print("下一步: python glucose-ml/scripts/seed_demo_glucose.py --register-id 9001")

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
