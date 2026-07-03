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
MIGRATE_027 = Path(__file__).resolve().parent / "migrate_027_follow_up_demo_enrichment.sql"
MIGRATE_028 = Path(__file__).resolve().parent / "migrate_028_follow_up_last_visit_lab.sql"

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

EXTRA_REGISTERS = [
    {
        "register_id": 9011,
        "patient_id": 9001,
        "dept_id": 9,
        "case_number": "BL20260626011",
        "real_name": "张糖友",
    },
]

DEFAULT_PASSWORD = "followup123"
PATIENT_PASSWORD = "patient123"

def _lab_panel(items: list[dict]) -> list[dict]:
    return [
        {
            "code": item["metric_code"],
            "label": item["label"],
            "value": item["metric_value"],
            "unit": item["unit"],
            "refRange": item["ref_range"],
            "flag": item["abnormal_flag"],
        }
        for item in items
    ]


LAST_VISIT_LAB_ITEMS = {
    9001: [
        {"metric_code": "hba1c", "label": "糖化血红蛋白", "metric_value": 7.8, "unit": "%", "ref_range": "4.0-6.0", "abnormal_flag": "high", "sort_order": 1},
        {"metric_code": "fasting_glucose", "label": "空腹血糖", "metric_value": 8.2, "unit": "mmol/L", "ref_range": "3.9-6.1", "abnormal_flag": "high", "sort_order": 2},
        {"metric_code": "postprandial_glucose", "label": "餐后2h血糖", "metric_value": 11.5, "unit": "mmol/L", "ref_range": "<7.8", "abnormal_flag": "high", "sort_order": 3},
        {"metric_code": "ldl_c", "label": "低密度脂蛋白", "metric_value": 3.4, "unit": "mmol/L", "ref_range": "<2.6", "abnormal_flag": "high", "sort_order": 4},
        {"metric_code": "urine_microalbumin", "label": "尿微量白蛋白", "metric_value": 28, "unit": "mg/L", "ref_range": "<30", "abnormal_flag": "normal", "sort_order": 5},
    ],
    9002: [
        {"metric_code": "hba1c", "label": "糖化血红蛋白", "metric_value": 7.2, "unit": "%", "ref_range": "4.0-6.0", "abnormal_flag": "high", "sort_order": 1},
        {"metric_code": "fasting_glucose", "label": "空腹血糖", "metric_value": 7.6, "unit": "mmol/L", "ref_range": "3.9-6.1", "abnormal_flag": "high", "sort_order": 2},
        {"metric_code": "postprandial_glucose", "label": "餐后2h血糖", "metric_value": 10.8, "unit": "mmol/L", "ref_range": "<7.8", "abnormal_flag": "high", "sort_order": 3},
        {"metric_code": "ldl_c", "label": "低密度脂蛋白", "metric_value": 2.8, "unit": "mmol/L", "ref_range": "<1.8", "abnormal_flag": "high", "sort_order": 4},
    ],
    9003: [
        {"metric_code": "hba1c", "label": "糖化血红蛋白", "metric_value": 7.5, "unit": "%", "ref_range": "4.0-6.0", "abnormal_flag": "high", "sort_order": 1},
        {"metric_code": "fasting_glucose", "label": "空腹血糖", "metric_value": 7.9, "unit": "mmol/L", "ref_range": "3.9-6.1", "abnormal_flag": "high", "sort_order": 2},
        {"metric_code": "postprandial_glucose", "label": "餐后2h血糖", "metric_value": 11.0, "unit": "mmol/L", "ref_range": "<7.8", "abnormal_flag": "high", "sort_order": 3},
    ],
    9011: [
        {"metric_code": "esr", "label": "血沉", "metric_value": 18, "unit": "mm/h", "ref_range": "0-20", "abnormal_flag": "normal", "sort_order": 1},
        {"metric_code": "crp", "label": "C反应蛋白", "metric_value": 5.2, "unit": "mg/L", "ref_range": "<10", "abnormal_flag": "normal", "sort_order": 2},
        {"metric_code": "rf", "label": "类风湿因子", "metric_value": 12, "unit": "IU/mL", "ref_range": "<20", "abnormal_flag": "normal", "sort_order": 3},
    ],
}

LAST_VISIT_SNAPSHOTS = {
    9001: {
        "diagnosis_summary": "2型糖尿病 · 血糖控制一般，建议加强居家监测",
        "doctor_name": "内分泌科医师",
        "department_name": "内分泌科",
        "chief_complaint": "血糖控制不佳复诊",
        "treatment_advice": "继续二甲双胍，加强血糖监测与生活方式干预。",
        "metrics": {
            "hba1c": {"value": 7.8, "unit": "%", "label": "糖化血红蛋白", "abnormalFlag": "high"},
            "fasting_glucose": {"value": 8.2, "unit": "mmol/L", "label": "空腹血糖", "abnormalFlag": "high"},
            "postprandial_glucose": {"value": 11.5, "unit": "mmol/L", "label": "餐后2h血糖", "abnormalFlag": "high"},
        },
    },
    9002: {
        "diagnosis_summary": "2型糖尿病合并冠心病 · 需关注血糖与心血管风险",
        "doctor_name": "心血管科医师",
        "department_name": "心血管科",
        "chief_complaint": "糖尿病合并冠心病随访",
        "treatment_advice": "控制血糖，继续心血管二级预防用药。",
        "metrics": {
            "hba1c": {"value": 7.2, "unit": "%", "label": "糖化血红蛋白", "abnormalFlag": "high"},
            "fasting_glucose": {"value": 7.6, "unit": "mmol/L", "label": "空腹血糖", "abnormalFlag": "high"},
            "postprandial_glucose": {"value": 10.8, "unit": "mmol/L", "label": "餐后2h血糖", "abnormalFlag": "high"},
        },
    },
    9003: {
        "diagnosis_summary": "2型糖尿病 · 合并轻度慢阻肺，注意感染期血糖波动",
        "doctor_name": "呼吸科医师",
        "department_name": "呼吸科",
        "chief_complaint": "慢阻肺合并糖尿病复诊",
        "treatment_advice": "监测血糖，预防感染期血糖波动。",
        "metrics": {
            "hba1c": {"value": 7.5, "unit": "%", "label": "糖化血红蛋白", "abnormalFlag": "high"},
            "fasting_glucose": {"value": 7.9, "unit": "mmol/L", "label": "空腹血糖", "abnormalFlag": "high"},
            "postprandial_glucose": {"value": 11.0, "unit": "mmol/L", "label": "餐后2h血糖", "abnormalFlag": "high"},
        },
    },
    9011: {
        "diagnosis_summary": "膝关节骨性关节炎 · 建议减少负重并康复锻炼",
        "doctor_name": "骨科医师",
        "department_name": "骨科",
        "chief_complaint": "右膝疼痛活动受限",
        "treatment_advice": "口服 NSAIDs，物理治疗，必要时关节腔注射。",
        "metrics": {
            "esr": {"value": 18, "unit": "mm/h", "label": "血沉", "abnormalFlag": "normal"},
            "crp": {"value": 5.2, "unit": "mg/L", "label": "C反应蛋白", "abnormalFlag": "normal"},
        },
    },
}


CLINICAL_PROFILES = {
    9001: {
        "medical_record_id": 59001,
        "readme": "血糖控制不佳复诊",
        "present": "既往 2 型糖尿病 5 年，近期空腹血糖波动偏大。",
        "proposal": "继续二甲双胍，加强血糖监测与生活方式干预。",
        "diagnosis": "2型糖尿病",
        "drug_keyword": "二甲双胍",
        "drug_usage": "口服，每日2次，每次0.5g，餐中服用",
    },
    9002: {
        "medical_record_id": 59002,
        "readme": "糖尿病合并冠心病随访",
        "present": "2 型糖尿病 8 年，冠心病支架术后 2 年，近期胸闷偶发。",
        "proposal": "控制血糖，继续心血管二级预防用药。",
        "diagnosis": "2型糖尿病合并冠心病",
        "drug_keyword": "阿卡波糖",
        "drug_usage": "口服，每日3次，每次50mg，随餐服用",
    },
    9003: {
        "medical_record_id": 59003,
        "readme": "慢阻肺合并糖尿病复诊",
        "present": "2 型糖尿病 6 年，慢阻肺稳定期，冬季易咳嗽。",
        "proposal": "监测血糖，预防感染期血糖波动。",
        "diagnosis": "2型糖尿病",
        "drug_keyword": "二甲双胍",
        "drug_usage": "口服，每日2次，每次0.5g",
    },
    9011: {
        "medical_record_id": 59101,
        "readme": "右膝疼痛活动受限",
        "present": "右膝关节疼痛 3 月，上下楼梯加重，无外伤史。",
        "proposal": "减少负重活动，口服 NSAIDs，康复锻炼。",
        "diagnosis": "膝关节骨性关节炎",
        "drug_keyword": "布洛芬",
        "drug_usage": "口服，每日2次，每次0.2g，餐后服用",
    },
}

HISTORY_SEEDS = [
    ("observation_confirmed", "患者纳入随访管理", "已完成科室在管登记"),
    ("interview_scheduled", "首次访谈已安排", "纳入后一周内电话随访"),
    ("glucose_entry", "早期居家血糖录入", "空腹血糖 7.2 mmol/L"),
    ("communication_message", "随访沟通提醒", "请按时记录居家血糖并关注复诊提醒"),
]


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


def table_exists(cur, table: str) -> bool:
    cur.execute(
        """
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = %s
        """,
        (table,),
    )
    return cur.fetchone() is not None


def ensure_migrate_027_schema(cur) -> None:
    """幂等应用 migrate_027，种子依赖 follow_up_history_event 与快照扩展列。"""
    if not MIGRATE_027.exists():
        raise SystemExit(f"缺少迁移文件: {MIGRATE_027}")

    sql = MIGRATE_027.read_text(encoding="utf-8")
    cur.execute(sql)

    after_cols = table_columns(cur, "follow_up_last_visit_snapshot")
    if "source_medical_record_id" not in after_cols:
        raise SystemExit("migrate_027 执行后仍缺少 follow_up_last_visit_snapshot.source_medical_record_id")


def ensure_migrate_028_schema(cur) -> None:
    """幂等应用 migrate_028，上次看诊检验面板与 lab_item 表。"""
    if not MIGRATE_028.exists():
        raise SystemExit(f"缺少迁移文件: {MIGRATE_028}")

    cur.execute(MIGRATE_028.read_text(encoding="utf-8"))

    if not table_exists(cur, "follow_up_last_visit_lab_item"):
        raise SystemExit("migrate_028 执行后仍缺少 follow_up_last_visit_lab_item")


def ensure_last_visit_lab_items(cur, rid: int) -> None:
    items = LAST_VISIT_LAB_ITEMS.get(rid)
    if not items or not table_exists(cur, "follow_up_last_visit_lab_item"):
        return

    for item in items:
        cur.execute(
            """
            INSERT INTO follow_up_last_visit_lab_item (
                register_id, metric_code, label, metric_value, unit, ref_range, abnormal_flag, sort_order
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (register_id, metric_code) DO UPDATE SET
                label = EXCLUDED.label,
                metric_value = EXCLUDED.metric_value,
                unit = EXCLUDED.unit,
                ref_range = EXCLUDED.ref_range,
                abnormal_flag = EXCLUDED.abnormal_flag,
                sort_order = EXCLUDED.sort_order
            """,
            (
                rid,
                item["metric_code"],
                item["label"],
                item["metric_value"],
                item["unit"],
                item["ref_range"],
                item["abnormal_flag"],
                item["sort_order"],
            ),
        )


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


def ensure_clinical_data(cur, rid: int, dept_id: int, snap: dict) -> None:
    profile = CLINICAL_PROFILES.get(rid)
    if not profile:
        return

    mr_cols = table_columns(cur, "medical_record")
    mr_id = profile["medical_record_id"]
    mr_row = {"id": mr_id, "register_id": rid}
    if "chief_complaint" in mr_cols:
        mr_row.update(
            {
                "chief_complaint": profile["readme"],
                "present_illness": profile["present"],
                "treatment_proposal": profile["proposal"],
                "diagnosis": profile["diagnosis"],
                "preliminary_diagnosis": profile["diagnosis"],
            }
        )
    else:
        mr_row.update(
            {
                "readme": profile["readme"],
                "present": profile["present"],
                "proposal": profile["proposal"],
                "diagnosis": profile["diagnosis"],
            }
        )
    mr_row = {k: v for k, v in mr_row.items() if k in mr_cols}
    cur.execute(f"SELECT 1 FROM medical_record WHERE id = %s", (mr_id,))
    if not cur.fetchone():
        cols = list(mr_row.keys())
        cur.execute(
            f"INSERT INTO medical_record ({', '.join(cols)}) VALUES ({', '.join(['%s'] * len(cols))})",
            tuple(mr_row[c] for c in cols),
        )

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
        (mr_id, mr_id),
    )

    cur.execute(
        """
        INSERT INTO prescription (register_id, drug_id, drug_usage, drug_number, creation_time)
        SELECT %s, d.id, %s, '按医嘱', CURRENT_TIMESTAMP - INTERVAL '14 days'
        FROM drug_info d
        WHERE d.drug_name LIKE %s
          AND NOT EXISTS (SELECT 1 FROM prescription WHERE register_id = %s)
        LIMIT 1
        """,
        (rid, profile["drug_usage"], f"%{profile['drug_keyword']}%", rid),
    )

    cur.execute(
        """
        SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'drugId', pr.drug_id,
            'drugName', di.drug_name,
            'drugUsage', pr.drug_usage,
            'drugNumber', pr.drug_number
        )), '[]'::jsonb)
        FROM prescription pr
        INNER JOIN drug_info di ON di.id = pr.drug_id
        WHERE pr.register_id = %s
        """,
        (rid,),
    )
    prescription_summary = cur.fetchone()[0]
    if isinstance(prescription_summary, (dict, list)):
        prescription_json = json.dumps(prescription_summary, ensure_ascii=False)
    else:
        prescription_json = prescription_summary

    snapshot_cols = table_columns(cur, "follow_up_last_visit_snapshot")
    lab_panel_json = json.dumps(_lab_panel(LAST_VISIT_LAB_ITEMS.get(rid, [])), ensure_ascii=False)
    metrics_json = json.dumps(snap["metrics"], ensure_ascii=False)
    if "source_medical_record_id" in snapshot_cols and "prescription_summary" in snapshot_cols:
        if "lab_panel" in snapshot_cols and "chief_complaint" in snapshot_cols:
            cur.execute(
                """
                INSERT INTO follow_up_last_visit_snapshot (
                    register_id, visit_date, diagnosis_summary, professional_metrics,
                    doctor_name, department_name, source_medical_record_id, prescription_summary,
                    chief_complaint, treatment_advice, lab_panel
                )
                VALUES (
                    %s, (CURRENT_DATE - INTERVAL '14 days')::date, %s, %s::jsonb, %s, %s, %s, %s::jsonb, %s, %s, %s::jsonb
                )
                ON CONFLICT (register_id) DO UPDATE SET
                    visit_date = EXCLUDED.visit_date,
                    diagnosis_summary = EXCLUDED.diagnosis_summary,
                    professional_metrics = EXCLUDED.professional_metrics,
                    doctor_name = EXCLUDED.doctor_name,
                    department_name = EXCLUDED.department_name,
                    source_medical_record_id = EXCLUDED.source_medical_record_id,
                    prescription_summary = EXCLUDED.prescription_summary,
                    chief_complaint = EXCLUDED.chief_complaint,
                    treatment_advice = EXCLUDED.treatment_advice,
                    lab_panel = EXCLUDED.lab_panel,
                    updated_at = CURRENT_TIMESTAMP
                """,
                (
                    rid,
                    snap["diagnosis_summary"],
                    metrics_json,
                    snap["doctor_name"],
                    snap["department_name"],
                    mr_id,
                    prescription_json,
                    snap.get("chief_complaint"),
                    snap.get("treatment_advice"),
                    lab_panel_json,
                ),
            )
        else:
            cur.execute(
                """
                INSERT INTO follow_up_last_visit_snapshot (
                    register_id, visit_date, diagnosis_summary, professional_metrics,
                    doctor_name, department_name, source_medical_record_id, prescription_summary
                )
                VALUES (%s, (CURRENT_DATE - INTERVAL '14 days')::date, %s, %s::jsonb, %s, %s, %s, %s::jsonb)
                ON CONFLICT (register_id) DO UPDATE SET
                    visit_date = EXCLUDED.visit_date,
                    diagnosis_summary = EXCLUDED.diagnosis_summary,
                    professional_metrics = EXCLUDED.professional_metrics,
                    doctor_name = EXCLUDED.doctor_name,
                    department_name = EXCLUDED.department_name,
                    source_medical_record_id = EXCLUDED.source_medical_record_id,
                    prescription_summary = EXCLUDED.prescription_summary,
                    updated_at = CURRENT_TIMESTAMP
                """,
                (
                    rid,
                    snap["diagnosis_summary"],
                    metrics_json,
                    snap["doctor_name"],
                    snap["department_name"],
                    mr_id,
                    prescription_json,
                ),
            )
    else:
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

    ensure_last_visit_lab_items(cur, rid)

    if not table_exists(cur, "follow_up_history_event"):
        return

    for idx, (event_type, title, summary) in enumerate(HISTORY_SEEDS):
        cur.execute(
            """
            INSERT INTO follow_up_history_event (
                register_id, department_id, event_type, actor_type, title, summary, payload, occurred_at
            )
            SELECT %s, %s, %s, 'system', %s, %s, %s::jsonb, CURRENT_TIMESTAMP - (%s || ' days')::interval
            WHERE NOT EXISTS (
                SELECT 1 FROM follow_up_history_event
                WHERE register_id = %s AND event_type = %s AND title = %s
            )
            """,
            (
                rid,
                dept_id,
                event_type,
                title,
                summary,
                json.dumps({"seed": True, "summary": summary}, ensure_ascii=False),
                str(7 - idx),
                rid,
                event_type,
                title,
            ),
        )


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

    if rid in (9001, 9002, 9003):
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

    if rid == 9001:
        ensure_glucose_bootstrap(cur, rid)

    snap = LAST_VISIT_SNAPSHOTS.get(rid)
    if snap:
        ensure_clinical_data(cur, rid, dept_id, snap)


def ensure_glucose_bootstrap(cur, rid: int) -> None:
    """张糖友：写入 48 小时模型输入基线；前端图表仍只展示最早 5 个 uci 点。"""
    if rid != 9001:
        return

    cur.execute(
        "DELETE FROM patient_health_observation WHERE register_id = %s AND source_type = 'uci_import'",
        (rid,),
    )
    cur.execute(
        """
        INSERT INTO patient_health_observation (
            register_id, observed_at, metric_code, metric_value, unit, source_type, note
        )
        SELECT
            %s,
            date_trunc('hour', CURRENT_TIMESTAMP) - (n || ' hours')::interval,
            'blood_glucose',
            ROUND((6.8 + 0.35 * sin(n::float / 4.0))::numeric, 2),
            'mmol/L',
            'uci_import',
            '模型输入基线(48h)'
        FROM generate_series(47, 0, -1) AS n
        WHERE NOT EXISTS (
            SELECT 1 FROM patient_health_observation
            WHERE register_id = %s AND source_type = 'uci_import'
        )
        """,
        (rid, rid),
    )


def ensure_extra_register(cur, extra: dict) -> None:
    rid = extra["register_id"]
    pid = extra["patient_id"]
    dept_id = extra["dept_id"]

    cur.execute("SELECT id FROM employee WHERE deptment_id = %s AND delmark = 0 ORDER BY id LIMIT 1", (dept_id,))
    emp = cur.fetchone()
    employee_id = emp[0] if emp else 1

    cur.execute("SELECT 1 FROM register WHERE id = %s", (rid,))
    if not cur.fetchone():
        cur.execute(
            """
            INSERT INTO register (
                id, case_number, real_name, gender, birthdate, age, age_type, home_address,
                visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
                is_book, regist_method, regist_money, visit_state, patient_id
            ) VALUES (
                %s, %s, %s, '男', '1990-01-01', 36, '年', '沈阳市演示区随访路 1 号',
                CURRENT_TIMESTAMP - INTERVAL '21 days', '上午', %s, %s, 2, 2,
                '否', '医保', 15.00, 3, %s
            )
            """,
            (rid, extra["case_number"], extra["real_name"], dept_id, employee_id, pid),
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
                visit_date = COALESCE(visit_date, CURRENT_TIMESTAMP - INTERVAL '21 days')
            WHERE id = %s
            """,
            (extra["case_number"], extra["real_name"], dept_id, pid, rid),
        )

    snap = LAST_VISIT_SNAPSHOTS.get(rid)
    if snap:
        ensure_clinical_data(cur, rid, dept_id, snap)


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

    print("检查/应用 migrate_027 演示扩展表结构...")
    ensure_migrate_027_schema(cur)
    print("检查/应用 migrate_028 上次看诊检验表结构...")
    ensure_migrate_028_schema(cur)
    print("  follow_up_history_event、快照扩展列已就绪")

    print("创建随访护士账号...")
    for n in NURSES:
        eid = ensure_nurse(cur, n["username"], n["realname"], n["dept_id"])
        print(f"  {n['username']} / {DEFAULT_PASSWORD}  科室={n['dept_id']}  employee_id={eid}")

    print("创建/对齐在管演示患者...")
    for p in PATIENTS:
        ensure_patient_bundle(cur, p, patient_cols)
        print(f"  register {p['register_id']} {p['real_name']} 科室={p['dept_id']}  账号 {p['username']}/{PATIENT_PASSWORD}")

    print("补充同一患者多科室看诊记录...")
    for extra in EXTRA_REGISTERS:
        ensure_extra_register(cur, extra)
        print(f"  register {extra['register_id']} {extra['real_name']} 科室={extra['dept_id']} (patient {extra['patient_id']})")

    print("完成。护士登录后直达疗效评估；患者可录入血糖并与护士沟通。")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
