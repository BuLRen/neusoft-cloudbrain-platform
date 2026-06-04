-- ============================================================
-- 东软云医院系统 - 数据库初始化脚本
-- 数据库: PostgreSQL 16.x
-- 数据库名: xikang_hospital
-- 字符集: UTF-8
-- 时区: Asia/Shanghai
-- ============================================================

-- docker-compose 已通过 POSTGRES_DB 创建 xikang_hospital。
-- init 脚本会自动在该数据库内执行，避免重复 CREATE DATABASE 导致首次启动失败。

-- 设置时区
SET timezone = 'Asia/Shanghai';

-- ============================================================
-- 第 1 层：基础表（无外键依赖）
-- ============================================================

-- ============================================================
-- 表: users (用户表)
-- 说明: 系统用户表，用于登录认证（管理员、医生、挂号员、医技、药房、患者）
-- user_type: 1-管理员, 2-医生, 3-挂号员, 4-医技人员, 5-药房人员, 6-患者
-- ============================================================
CREATE TABLE users (
    id              BIGSERIAL      PRIMARY KEY,
    username        VARCHAR(64)    NOT NULL UNIQUE,
    password        VARCHAR(255)   NOT NULL,
    real_name       VARCHAR(64)    DEFAULT NULL,
    email           VARCHAR(128)   DEFAULT NULL,
    phone           VARCHAR(20)    DEFAULT NULL,
    id_card         VARCHAR(18)    DEFAULT NULL,
    gender          VARCHAR(6)     DEFAULT NULL,
    birthdate       DATE           DEFAULT NULL,
    avatar          VARCHAR(255)   DEFAULT NULL,
    patient_id      INTEGER        DEFAULT NULL,
    status          INTEGER        NOT NULL DEFAULT 1,
    user_type       INTEGER        NOT NULL DEFAULT 6,
    remark          VARCHAR(255)   DEFAULT NULL,
    create_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status CHECK (status IN (1, 0, -1)),
    CONSTRAINT chk_users_gender CHECK (gender IS NULL OR gender IN ('男', '女'))
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_id_card ON users(id_card);

COMMENT ON TABLE users IS '系统用户表（登录认证）';
COMMENT ON COLUMN users.user_type IS '用户类型: 1-管理员, 2-医生, 3-挂号员, 4-医技人员, 5-药房人员, 6-患者';
COMMENT ON COLUMN users.id_card IS '身份证号，用于实名认证和医保关联';
COMMENT ON COLUMN users.gender IS '性别: 男/女';
COMMENT ON COLUMN users.birthdate IS '出生日期';
COMMENT ON COLUMN users.avatar IS '头像URL';
COMMENT ON COLUMN users.patient_id IS '关联的患者档案ID';

-- users 表外键关联 patient 表（需要单独创建，因为 patient 在后面定义）
-- ALTER TABLE users ADD CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE SET NULL;

-- ============================================================
-- 表: department (科室表)
-- 说明: 存储医院科室信息，如内科、外科、骨科等
-- ============================================================
CREATE TABLE department (
    id              SERIAL          PRIMARY KEY,
    dept_code       VARCHAR(64)     NOT NULL,
    dept_name       VARCHAR(64)     NOT NULL,
    dept_type       VARCHAR(64)     DEFAULT NULL,
    delmark         SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT uk_department_dept_code UNIQUE (dept_code),
    CONSTRAINT chk_department_delmark CHECK (delmark IN (0, 1))
);

COMMENT ON TABLE department IS '科室表';
COMMENT ON COLUMN department.id IS '主键ID';
COMMENT ON COLUMN department.dept_code IS '科室编码，如: SJNK(神经内科)';
COMMENT ON COLUMN department.dept_name IS '科室名称';
COMMENT ON COLUMN department.dept_type IS '科室类型: 临床科室、医技科室等';
COMMENT ON COLUMN department.delmark IS '生效标记: 1-正常, 0-已删除';

-- ============================================================
-- 表: regist_level (挂号级别表)
-- 说明: 定义挂号等级和对应费用，如普通号、专家号、主任号
-- ============================================================
CREATE TABLE regist_level (
    id              SERIAL          PRIMARY KEY,
    regist_code     VARCHAR(64)     NOT NULL,
    regist_name     VARCHAR(64)     NOT NULL,
    regist_fee      DECIMAL(8,2)    NOT NULL DEFAULT 0.00,
    regist_quota    INTEGER         DEFAULT 0,
    sequence_no     INTEGER         DEFAULT 0,
    delmark         SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT uk_regist_level_code UNIQUE (regist_code),
    CONSTRAINT chk_regist_level_fee CHECK (regist_fee >= 0),
    CONSTRAINT chk_regist_level_quota CHECK (regist_quota >= 0),
    CONSTRAINT chk_regist_level_delmark CHECK (delmark IN (0, 1))
);

COMMENT ON TABLE regist_level IS '挂号级别表';
COMMENT ON COLUMN regist_level.id IS '主键ID';
COMMENT ON COLUMN regist_level.regist_code IS '号别编码';
COMMENT ON COLUMN regist_level.regist_name IS '号别名称';
COMMENT ON COLUMN regist_level.regist_fee IS '挂号费（元）';
COMMENT ON COLUMN regist_level.regist_quota IS '每半天挂号限额';
COMMENT ON COLUMN regist_level.sequence_no IS '显示顺序号';
COMMENT ON COLUMN regist_level.delmark IS '生效标记: 1-正常, 0-已删除';

-- ============================================================
-- 表: scheduling (排班表)
-- 说明: 医生出诊排班规则，按星期+午别编排
-- ============================================================
CREATE TABLE scheduling (
    id              SERIAL          PRIMARY KEY,
    rule_name       VARCHAR(64)     NOT NULL,
    week_rule       VARCHAR(14)     DEFAULT NULL,
    delmark         SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT chk_scheduling_delmark CHECK (delmark IN (0, 1))
);

COMMENT ON TABLE scheduling IS '排班规则表';
COMMENT ON COLUMN scheduling.week_rule IS '星期规则，格式: 数字+午别，如 1上3上5上 表示周一三五上午';

-- ============================================================
-- 表: settle_category (结算类别表)
-- 说明: 患者的费用结算方式，如自费、医保、新农合
-- ============================================================
CREATE TABLE settle_category (
    id              SERIAL          PRIMARY KEY,
    settle_code     VARCHAR(64)     NOT NULL,
    settle_name     VARCHAR(64)     NOT NULL,
    sequence_no     INTEGER         DEFAULT 0,
    delmark         SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT uk_settle_category_code UNIQUE (settle_code),
    CONSTRAINT chk_settle_category_delmark CHECK (delmark IN (0, 1))
);

COMMENT ON TABLE settle_category IS '结算类别表';

-- ============================================================
-- 表: patient (患者档案表)
-- 说明: 患者详细信息档案，用于帮家人挂号场景
-- ============================================================
CREATE TABLE patient (
    id              SERIAL          PRIMARY KEY,
    real_name       VARCHAR(64)     NOT NULL,
    id_card         VARCHAR(18)     NOT NULL,
    gender          VARCHAR(6)      NOT NULL,
    birthdate       DATE            NOT NULL,
    phone           VARCHAR(20)     DEFAULT NULL,
    avatar          VARCHAR(255)    DEFAULT NULL,
    home_address    VARCHAR(255)    DEFAULT NULL,
    allergy_history VARCHAR(512)    DEFAULT NULL,
    relation        VARCHAR(16)     DEFAULT '本人',
    is_primary      SMALLINT       NOT NULL DEFAULT 0,
    delmark         SMALLINT       NOT NULL DEFAULT 1,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_patient_id_card UNIQUE (id_card),
    CONSTRAINT chk_patient_gender CHECK (gender IN ('男', '女')),
    CONSTRAINT chk_patient_relation CHECK (relation IN ('本人', '父亲', '母亲', '配偶', '子女', '祖父', '祖母', '外祖父', '外祖母', '兄弟', '姐妹', '其他')),
    CONSTRAINT chk_patient_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_patient_delmark CHECK (delmark IN (0, 1))
);

CREATE INDEX idx_patient_id_card ON patient(id_card);

COMMENT ON TABLE patient IS '患者档案表';
COMMENT ON COLUMN patient.id_card IS '身份证号，唯一';
COMMENT ON COLUMN patient.allergy_history IS '过敏史';
COMMENT ON COLUMN patient.relation IS '与账号本人的关系: 本人、父亲、母亲、配偶、子女...';
COMMENT ON COLUMN patient.is_primary IS '是否为本账号本人: 1-是, 0-否(家人)';

-- ============================================================
-- 表: user_patient_managed (用户管理患者关联表)
-- 说明: 一个用户可以管理多个患者（本人+家人），用于帮家人挂号
-- ============================================================
CREATE TABLE user_patient_managed (
    user_id         INTEGER         NOT NULL,
    patient_id      INTEGER         NOT NULL,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, patient_id),

    CONSTRAINT fk_upm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_upm_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

CREATE INDEX idx_upm_user ON user_patient_managed(user_id);
CREATE INDEX idx_upm_patient ON user_patient_managed(patient_id);

COMMENT ON TABLE user_patient_managed IS '用户管理的患者列表（本人+家人）';

-- ============================================================
-- 表: disease (疾病表)
-- 说明: 疾病字典表，包含 ICD 编码，供病历诊断和 AI 诊断使用
-- ============================================================
CREATE TABLE disease (
    id                  SERIAL          PRIMARY KEY,
    disease_code        VARCHAR(64)     DEFAULT NULL,
    disease_name        VARCHAR(255)    NOT NULL,
    diseaseicd          VARCHAR(64)     DEFAULT NULL,
    disease_category    VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT uk_disease_icd UNIQUE (diseaseicd)
);

CREATE INDEX idx_disease_name ON disease(disease_name);
CREATE INDEX idx_disease_category ON disease(disease_category);

COMMENT ON TABLE disease IS '疾病字典表';
COMMENT ON COLUMN disease.diseaseicd IS '国际ICD编码，如: G43.9(偏头痛)';

-- ============================================================
-- 表: drug_info (药品信息表)
-- 说明: 药品目录，包含药品编码、名称、规格、单价等信息
-- ============================================================
CREATE TABLE drug_info (
    id              SERIAL          PRIMARY KEY,
    drug_code       VARCHAR(255)    NOT NULL,
    drug_name       VARCHAR(255)    NOT NULL,
    drug_format     VARCHAR(255)    DEFAULT NULL,
    drug_unit       VARCHAR(16)     DEFAULT NULL,
    manufacturer    VARCHAR(255)    DEFAULT NULL,
    drug_dosage     VARCHAR(64)     DEFAULT NULL,
    drug_type       VARCHAR(64)     DEFAULT NULL,
    drug_price      DECIMAL(8,2)    NOT NULL DEFAULT 0.00,
    mnemonic_code   VARCHAR(255)    DEFAULT NULL,
    creation_date   DATE            DEFAULT CURRENT_DATE,

    CONSTRAINT uk_drug_code UNIQUE (drug_code),
    CONSTRAINT chk_drug_price CHECK (drug_price >= 0)
);

CREATE INDEX idx_drug_name ON drug_info(drug_name);
CREATE INDEX idx_drug_mnemonic ON drug_info(mnemonic_code);
CREATE INDEX idx_drug_type ON drug_info(drug_type);

COMMENT ON TABLE drug_info IS '药品信息表';
COMMENT ON COLUMN drug_info.mnemonic_code IS '拼音助记码，用于快速搜索';

-- ============================================================
-- 表: medical_technology (医技项目表)
-- 说明: 检查、检验、处置等医疗技术项目的统一目录
-- ============================================================
CREATE TABLE medical_technology (
    id              SERIAL          PRIMARY KEY,
    tech_code       VARCHAR(64)     NOT NULL,
    tech_name       VARCHAR(64)     NOT NULL,
    tech_format     VARCHAR(64)     DEFAULT NULL,
    tech_price      DECIMAL(8,2)    NOT NULL DEFAULT 0.00,
    tech_type       VARCHAR(64)     NOT NULL,
    price_type      VARCHAR(64)     DEFAULT NULL,
    deptment_id     INTEGER         DEFAULT NULL,

    CONSTRAINT fk_medtech_department
        FOREIGN KEY (deptment_id) REFERENCES department(id)
        ON DELETE SET NULL,

    CONSTRAINT uk_medtech_code UNIQUE (tech_code),
    CONSTRAINT chk_medtech_price CHECK (tech_price >= 0),
    CONSTRAINT chk_medtech_type CHECK (tech_type IN ('check', 'inspection', 'disposal'))
);

CREATE INDEX idx_medtech_type ON medical_technology(tech_type);
CREATE INDEX idx_medtech_deptment_id ON medical_technology(deptment_id);

COMMENT ON TABLE medical_technology IS '医技项目表（检查/检验/处置统一目录）';
COMMENT ON COLUMN medical_technology.tech_type IS '项目类型: check-检查, inspection-检验, disposal-处置';

-- ============================================================
-- 第 2 层：员工表（依赖第 1 层）
-- ============================================================

-- ============================================================
-- 表: employee (医院员工表)
-- 说明: 存储医院员工信息，主要是坐诊医生，也包含挂号员、药师等
-- ============================================================
CREATE TABLE employee (
    id              SERIAL          PRIMARY KEY,
    deptment_id     INTEGER         DEFAULT NULL,
    regist_level_id INTEGER         DEFAULT NULL,
    scheduling_id   INTEGER         DEFAULT NULL,
    realname        VARCHAR(64)     NOT NULL,
    password        VARCHAR(64)     NOT NULL,
    delmark         SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT fk_employee_department
        FOREIGN KEY (deptment_id) REFERENCES department(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_employee_regist_level
        FOREIGN KEY (regist_level_id) REFERENCES regist_level(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_employee_scheduling
        FOREIGN KEY (scheduling_id) REFERENCES scheduling(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_employee_delmark CHECK (delmark IN (0, 1))
);

CREATE INDEX idx_employee_deptment_id ON employee(deptment_id);
CREATE INDEX idx_employee_regist_level_id ON employee(regist_level_id);

COMMENT ON TABLE employee IS '医院员工表';
COMMENT ON COLUMN employee.password IS '密码（BCrypt加密存储，禁止明文）';

-- ============================================================
-- 第 3 层：挂号表（依赖第 1、2 层）
-- ============================================================

-- ============================================================
-- 表: register (患者历次挂号信息表)
-- 说明: 记录患者每次挂号的全部信息，是业务流转的核心表
-- ============================================================
CREATE TABLE register (
    id                      SERIAL          PRIMARY KEY,
    case_number             VARCHAR(64)     NOT NULL,
    real_name               VARCHAR(64)     NOT NULL,
    gender                  VARCHAR(6)      DEFAULT NULL,
    card_number             VARCHAR(18)     DEFAULT NULL,
    birthdate               DATE            DEFAULT NULL,
    age                     INTEGER         DEFAULT NULL,
    age_type                VARCHAR(6)      DEFAULT NULL,
    home_address            VARCHAR(128)    DEFAULT NULL,
    visit_date              TIMESTAMP       DEFAULT NULL,
    noon                    VARCHAR(6)      DEFAULT NULL,
    deptment_id             INTEGER         NOT NULL,
    employee_id             INTEGER         NOT NULL,
    regist_level_id         INTEGER         NOT NULL,
    settle_category_id      INTEGER         DEFAULT NULL,
    is_book                 VARCHAR(2)      DEFAULT '否',
    regist_method           VARCHAR(10)     DEFAULT NULL,
    regist_money            DECIMAL(8,2)    DEFAULT 0.00,
    visit_state             SMALLINT        NOT NULL DEFAULT 1,

    CONSTRAINT fk_register_department
        FOREIGN KEY (deptment_id) REFERENCES department(id),
    CONSTRAINT fk_register_employee
        FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_register_regist_level
        FOREIGN KEY (regist_level_id) REFERENCES regist_level(id),
    CONSTRAINT fk_register_settle_category
        FOREIGN KEY (settle_category_id) REFERENCES settle_category(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_register_gender CHECK (gender IS NULL OR gender IN ('男', '女')),
    CONSTRAINT chk_register_noon CHECK (noon IS NULL OR noon IN ('上午', '下午')),
    CONSTRAINT chk_register_is_book CHECK (is_book IN ('是', '否')),
    CONSTRAINT chk_register_visit_state CHECK (visit_state IN (1, 2, 3, 4)),
    CONSTRAINT chk_register_regist_money CHECK (regist_money >= 0)
);

CREATE INDEX idx_register_case_number ON register(case_number);
CREATE INDEX idx_register_real_name ON register(real_name);
CREATE INDEX idx_register_visit_state ON register(visit_state);
CREATE INDEX idx_register_employee_id ON register(employee_id);
CREATE INDEX idx_register_deptment_id ON register(deptment_id);
CREATE INDEX idx_register_visit_date ON register(visit_date);

COMMENT ON TABLE register IS '患者历次挂号信息表';
COMMENT ON COLUMN register.case_number IS '病历号，同一患者多次挂号共用同一病历号';
COMMENT ON COLUMN register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号';

-- ============================================================
-- 第 4 层：业务表（依赖第 3 层）
-- ============================================================

-- ============================================================
-- 表: medical_record (患者病历表)
-- 说明: 医生对患者的完整病历记录，包含主诉、现病史、诊断、处理意见等
-- ============================================================
CREATE TABLE medical_record (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         NOT NULL,
    readme          VARCHAR(512)    DEFAULT NULL,
    present         VARCHAR(512)    DEFAULT NULL,
    present_treat   VARCHAR(512)    DEFAULT NULL,
    history         VARCHAR(512)    DEFAULT NULL,
    allergy         VARCHAR(512)    DEFAULT NULL,
    physique        VARCHAR(512)    DEFAULT NULL,
    proposal        VARCHAR(512)    DEFAULT NULL,
    careful         VARCHAR(512)    DEFAULT NULL,
    diagnosis       VARCHAR(512)    DEFAULT NULL,
    cure            VARCHAR(512)    DEFAULT NULL,

    CONSTRAINT fk_medical_record_register
        FOREIGN KEY (register_id) REFERENCES register(id),

    CONSTRAINT uk_medical_record_register UNIQUE (register_id)
);

COMMENT ON TABLE medical_record IS '患者病历表';

-- ============================================================
-- 表: check_request (检查申请表)
-- 说明: 门诊医生开立的检查申请及检查结果
-- ============================================================
CREATE TABLE check_request (
    id                      SERIAL          PRIMARY KEY,
    register_id             INTEGER         NOT NULL,
    medical_technology_id   INTEGER         NOT NULL,
    check_info              VARCHAR(512)    DEFAULT NULL,
    check_position          VARCHAR(255)    DEFAULT NULL,
    creation_time           TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    check_employee_id       INTEGER         DEFAULT NULL,
    inputcheck_employee_id  INTEGER         DEFAULT NULL,
    check_time              TIMESTAMP       DEFAULT NULL,
    check_result            VARCHAR(512)    DEFAULT NULL,
    check_state             VARCHAR(64)     NOT NULL DEFAULT '待检查',
    check_remark            VARCHAR(512)    DEFAULT NULL,

    CONSTRAINT fk_check_request_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_check_request_medtech
        FOREIGN KEY (medical_technology_id) REFERENCES medical_technology(id),
    CONSTRAINT fk_check_request_employee
        FOREIGN KEY (check_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_check_request_input_employee
        FOREIGN KEY (inputcheck_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_check_request_state CHECK (check_state IN ('待检查', '检查中', '已完成'))
);

CREATE INDEX idx_check_request_register_id ON check_request(register_id);
CREATE INDEX idx_check_request_state ON check_request(check_state);
CREATE INDEX idx_check_request_medtech_id ON check_request(medical_technology_id);

COMMENT ON TABLE check_request IS '检查申请表';
COMMENT ON COLUMN check_request.check_state IS '状态: 待检查 → 检查中 → 已完成';

-- ============================================================
-- 表: inspection_request (检验申请表)
-- 说明: 门诊医生开立的检验申请及检验结果
-- ============================================================
CREATE TABLE inspection_request (
    id                          SERIAL          PRIMARY KEY,
    register_id                 INTEGER         NOT NULL,
    medical_technology_id       INTEGER         NOT NULL,
    inspection_info             VARCHAR(512)    DEFAULT NULL,
    inspection_position         VARCHAR(255)    DEFAULT NULL,
    creation_time               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    inspection_employee_id      INTEGER         DEFAULT NULL,
    inputinspection_employee_id INTEGER         DEFAULT NULL,
    inspection_time             TIMESTAMP       DEFAULT NULL,
    inspection_result           VARCHAR(512)    DEFAULT NULL,
    inspection_state            VARCHAR(64)     NOT NULL DEFAULT '待检验',
    inspection_remark           VARCHAR(512)    DEFAULT NULL,

    CONSTRAINT fk_inspection_request_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_inspection_request_medtech
        FOREIGN KEY (medical_technology_id) REFERENCES medical_technology(id),
    CONSTRAINT fk_inspection_request_employee
        FOREIGN KEY (inspection_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_inspection_request_input_employee
        FOREIGN KEY (inputinspection_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_inspection_state CHECK (inspection_state IN ('待检验', '检验中', '已完成'))
);

CREATE INDEX idx_inspection_request_register_id ON inspection_request(register_id);
CREATE INDEX idx_inspection_request_state ON inspection_request(inspection_state);

COMMENT ON TABLE inspection_request IS '检验申请表';

-- ============================================================
-- 表: disposal_request (处置申请表)
-- 说明: 门诊医生开立的处置申请及处置结果
-- ============================================================
CREATE TABLE disposal_request (
    id                          SERIAL          PRIMARY KEY,
    register_id                 INTEGER         NOT NULL,
    medical_technology_id       INTEGER         NOT NULL,
    disposal_info               VARCHAR(512)    DEFAULT NULL,
    disposal_position           VARCHAR(255)    DEFAULT NULL,
    creation_time               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    disposal_employee_id        INTEGER         DEFAULT NULL,
    inputdisposal_employee_id   INTEGER         DEFAULT NULL,
    disposal_time               TIMESTAMP       DEFAULT NULL,
    disposal_result             VARCHAR(512)    DEFAULT NULL,
    disposal_state              VARCHAR(64)     NOT NULL DEFAULT '待处置',
    disposal_remark             VARCHAR(512)    DEFAULT NULL,

    CONSTRAINT fk_disposal_request_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_disposal_request_medtech
        FOREIGN KEY (medical_technology_id) REFERENCES medical_technology(id),
    CONSTRAINT fk_disposal_request_employee
        FOREIGN KEY (disposal_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_disposal_request_input_employee
        FOREIGN KEY (inputdisposal_employee_id) REFERENCES employee(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_disposal_state CHECK (disposal_state IN ('待处置', '处置中', '已完成'))
);

CREATE INDEX idx_disposal_request_register_id ON disposal_request(register_id);
CREATE INDEX idx_disposal_request_state ON disposal_request(disposal_state);

COMMENT ON TABLE disposal_request IS '处置申请表';

-- ============================================================
-- 表: prescription (处方表)
-- 说明: 医生开立的处方记录，每条记录对应一个药品
-- ============================================================
CREATE TABLE prescription (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         NOT NULL,
    drug_id         INTEGER         NOT NULL,
    drug_usage      VARCHAR(255)    DEFAULT NULL,
    drug_number     VARCHAR(255)    DEFAULT NULL,
    creation_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    drug_state      VARCHAR(64)     NOT NULL DEFAULT '未发',

    CONSTRAINT fk_prescription_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_prescription_drug
        FOREIGN KEY (drug_id) REFERENCES drug_info(id),

    CONSTRAINT chk_prescription_state CHECK (drug_state IN ('未发', '已发', '已退'))
);

CREATE INDEX idx_prescription_register_id ON prescription(register_id);
CREATE INDEX idx_prescription_drug_id ON prescription(drug_id);
CREATE INDEX idx_prescription_state ON prescription(drug_state);

COMMENT ON TABLE prescription IS '处方表（每条记录对应一个药品）';
COMMENT ON COLUMN prescription.drug_state IS '状态: 未发 → 已发，或 未发 → 已退';

-- ============================================================
-- 第 5 层：病历疾病关联表（依赖第 4 层）
-- ============================================================

-- ============================================================
-- 表: medical_record_disease (病历首页疾病关联表)
-- 说明: 病历与疾病的多对多关联
-- ============================================================
CREATE TABLE medical_record_disease (
    medical_record_id   INTEGER     NOT NULL,
    disease_id          INTEGER     NOT NULL,

    PRIMARY KEY (medical_record_id, disease_id),

    CONSTRAINT fk_mrd_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_record(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_mrd_disease
        FOREIGN KEY (disease_id) REFERENCES disease(id)
);

COMMENT ON TABLE medical_record_disease IS '病历-疾病关联表（多对多）';

-- ============================================================
-- 第 6 层：AI 记录表（依赖第 3、4 层）
-- ============================================================

-- ============================================================
-- 表: ai_triage_record (AI 导诊记录表)
-- 说明: 记录患者挂号前的 AI 智能导诊过程和推荐结果
-- ============================================================
CREATE TABLE ai_triage_record (
    id                      SERIAL          PRIMARY KEY,
    patient_name            VARCHAR(64)     DEFAULT NULL,
    patient_age             INTEGER         DEFAULT NULL,
    patient_gender          VARCHAR(6)      DEFAULT NULL,
    symptom_description     TEXT            NOT NULL,
    recommend_dept_id       INTEGER         DEFAULT NULL,
    recommend_dept_name     VARCHAR(64)     DEFAULT NULL,
    recommend_doctor_id     INTEGER         DEFAULT NULL,
    recommend_doctor_name   VARCHAR(64)     DEFAULT NULL,
    risk_level              VARCHAR(16)     NOT NULL DEFAULT 'normal',
    is_priority             SMALLINT        NOT NULL DEFAULT 0,
    ai_analysis             TEXT            DEFAULT NULL,
    register_id             INTEGER         DEFAULT NULL,
    triage_time             TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id                VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_triage_dept
        FOREIGN KEY (recommend_dept_id) REFERENCES department(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_ai_triage_doctor
        FOREIGN KEY (recommend_doctor_id) REFERENCES employee(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_ai_triage_register
        FOREIGN KEY (register_id) REFERENCES register(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_triage_gender CHECK (patient_gender IS NULL OR patient_gender IN ('男', '女')),
    CONSTRAINT chk_ai_triage_risk CHECK (risk_level IN ('normal', 'urgent', 'critical')),
    CONSTRAINT chk_ai_triage_priority CHECK (is_priority IN (0, 1))
);

CREATE INDEX idx_ai_triage_register_id ON ai_triage_record(register_id);
CREATE INDEX idx_ai_triage_risk_level ON ai_triage_record(risk_level);
CREATE INDEX idx_ai_triage_time ON ai_triage_record(triage_time);

COMMENT ON TABLE ai_triage_record IS 'AI导诊记录表';
COMMENT ON COLUMN ai_triage_record.register_id IS '关联挂号ID，患者实际挂号后回填';

-- ============================================================
-- 表: ai_consultation_record (AI 预问诊记录表)
-- 说明: 记录患者挂号后的 AI 预问诊多轮对话过程和最终摘要
-- ============================================================
CREATE TABLE ai_consultation_record (
    id                      SERIAL          PRIMARY KEY,
    register_id             INTEGER         NOT NULL,
    round_number            INTEGER         NOT NULL DEFAULT 1,
    ai_question             TEXT            DEFAULT NULL,
    patient_answer          TEXT            DEFAULT NULL,
    consultation_state      VARCHAR(16)     NOT NULL DEFAULT 'in_progress',
    chief_complaint         VARCHAR(512)    DEFAULT NULL,
    symptom_duration        VARCHAR(128)    DEFAULT NULL,
    history_summary         TEXT            DEFAULT NULL,
    allergy_summary         TEXT            DEFAULT NULL,
    medication_summary      TEXT            DEFAULT NULL,
    ai_summary              TEXT            DEFAULT NULL,
    suggested_exam          TEXT            DEFAULT NULL,
    creation_time           TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    completion_time         TIMESTAMP       DEFAULT NULL,
    model_id                VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_consult_register
        FOREIGN KEY (register_id) REFERENCES register(id),

    CONSTRAINT chk_ai_consult_state CHECK (consultation_state IN ('in_progress', 'completed', 'cancelled'))
);

CREATE INDEX idx_ai_consult_register_id ON ai_consultation_record(register_id);
CREATE INDEX idx_ai_consult_state ON ai_consultation_record(consultation_state);

COMMENT ON TABLE ai_consultation_record IS 'AI预问诊记录表（多轮对话）';

-- ============================================================
-- 表: ai_medical_record_log (AI 病历生成日志表)
-- 说明: 记录 AI 自动生成病历的请求、生成内容和医生的修改记录
-- ============================================================
CREATE TABLE ai_medical_record_log (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL,
    medical_record_id   INTEGER         DEFAULT NULL,
    source_type         VARCHAR(32)     NOT NULL,
    ai_readme           TEXT            DEFAULT NULL,
    ai_present          TEXT            DEFAULT NULL,
    ai_history          TEXT            DEFAULT NULL,
    ai_allergy          TEXT            DEFAULT NULL,
    ai_physique         TEXT            DEFAULT NULL,
    ai_diagnosis        TEXT            DEFAULT NULL,
    is_adopted          SMALLINT        NOT NULL DEFAULT 0,
    doctor_modification TEXT            DEFAULT NULL,
    generation_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id            VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_mrlog_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_mrlog_medical_record
        FOREIGN KEY (medical_record_id) REFERENCES medical_record(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_mrlog_source CHECK (source_type IN ('consultation', 'dictation', 'exam')),
    CONSTRAINT chk_ai_mrlog_adopted CHECK (is_adopted IN (0, 1, 2))
);

CREATE INDEX idx_ai_mrlog_register_id ON ai_medical_record_log(register_id);

COMMENT ON TABLE ai_medical_record_log IS 'AI病历生成日志表';
COMMENT ON COLUMN ai_medical_record_log.doctor_modification IS '医生修改内容，JSON格式记录被修改的字段和修改前后值';

-- ============================================================
-- 表: ai_exam_suggestion (AI 检查推荐表)
-- 说明: AI 根据患者病历自动推荐的检查/检验项目
-- ============================================================
CREATE TABLE ai_exam_suggestion (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         NOT NULL,
    tech_id         INTEGER         NOT NULL,
    tech_name       VARCHAR(64)     DEFAULT NULL,
    suggest_type    VARCHAR(16)     NOT NULL,
    suggest_reason  TEXT            DEFAULT NULL,
    priority        INTEGER         NOT NULL DEFAULT 1,
    is_adopted      SMALLINT        NOT NULL DEFAULT 0,
    creation_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id        VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_exam_sug_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_exam_sug_medtech
        FOREIGN KEY (tech_id) REFERENCES medical_technology(id),

    CONSTRAINT chk_ai_exam_sug_type CHECK (suggest_type IN ('check', 'inspection')),
    CONSTRAINT chk_ai_exam_sug_priority CHECK (priority IN (1, 2, 3)),
    CONSTRAINT chk_ai_exam_sug_adopted CHECK (is_adopted IN (0, 1))
);

CREATE INDEX idx_ai_exam_sug_register_id ON ai_exam_suggestion(register_id);

COMMENT ON TABLE ai_exam_suggestion IS 'AI检查/检验推荐表';

-- ============================================================
-- 表: ai_exam_analysis (AI 检查结果分析表)
-- 说明: AI 对检查/检验结果的智能分析报告
-- ============================================================
CREATE TABLE ai_exam_analysis (
    id                      SERIAL          PRIMARY KEY,
    register_id             INTEGER         NOT NULL,
    check_request_id        INTEGER         DEFAULT NULL,
    inspection_request_id   INTEGER         DEFAULT NULL,
    analysis_type           VARCHAR(16)     NOT NULL,
    original_result         TEXT            DEFAULT NULL,
    abnormal_indicators     TEXT            DEFAULT NULL,
    risk_level              VARCHAR(16)     NOT NULL DEFAULT 'normal',
    analysis_report         TEXT            DEFAULT NULL,
    correlation_analysis    TEXT            DEFAULT NULL,
    is_viewed               SMALLINT        NOT NULL DEFAULT 0,
    analysis_time           TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id                VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_exam_analysis_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_exam_analysis_check
        FOREIGN KEY (check_request_id) REFERENCES check_request(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_ai_exam_analysis_inspection
        FOREIGN KEY (inspection_request_id) REFERENCES inspection_request(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_exam_analysis_type CHECK (analysis_type IN ('check', 'inspection')),
    CONSTRAINT chk_ai_exam_analysis_risk CHECK (risk_level IN ('normal', 'attention', 'warning', 'danger')),
    CONSTRAINT chk_ai_exam_analysis_viewed CHECK (is_viewed IN (0, 1))
);

CREATE INDEX idx_ai_exam_analysis_register_id ON ai_exam_analysis(register_id);
CREATE INDEX idx_ai_exam_analysis_check_id ON ai_exam_analysis(check_request_id);
CREATE INDEX idx_ai_exam_analysis_inspection_id ON ai_exam_analysis(inspection_request_id);
CREATE INDEX idx_ai_exam_analysis_risk ON ai_exam_analysis(risk_level);

COMMENT ON TABLE ai_exam_analysis IS 'AI检查/检验结果分析表';
COMMENT ON COLUMN ai_exam_analysis.abnormal_indicators IS '异常指标，JSON格式，如: [{"指标":"白细胞","值":"15.2","参考值":"4-10","单位":"10^9/L"}]';

-- ============================================================
-- 表: ai_diagnosis_suggestion (AI 诊断建议表)
-- 说明: AI 根据病历+检查结果推荐的疑似疾病列表
-- ============================================================
CREATE TABLE ai_diagnosis_suggestion (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL,
    disease_id          INTEGER         DEFAULT NULL,
    disease_name        VARCHAR(255)    DEFAULT NULL,
    recommend_icd       VARCHAR(64)     DEFAULT NULL,
    probability         DECIMAL(5,2)    DEFAULT NULL,
    risk_level          VARCHAR(16)     NOT NULL DEFAULT 'low',
    treatment_direction TEXT            DEFAULT NULL,
    diagnosis_basis     TEXT            DEFAULT NULL,
    is_adopted          SMALLINT        NOT NULL DEFAULT 0,
    sort_order          INTEGER         NOT NULL DEFAULT 1,
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id            VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_diagnosis_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_diagnosis_disease
        FOREIGN KEY (disease_id) REFERENCES disease(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_diagnosis_risk CHECK (risk_level IN ('low', 'medium', 'high')),
    CONSTRAINT chk_ai_diagnosis_adopted CHECK (is_adopted IN (0, 1)),
    CONSTRAINT chk_ai_diagnosis_probability CHECK (probability IS NULL OR (probability >= 0 AND probability <= 100))
);

CREATE INDEX idx_ai_diagnosis_register_id ON ai_diagnosis_suggestion(register_id);
CREATE INDEX idx_ai_diagnosis_disease_id ON ai_diagnosis_suggestion(disease_id);

COMMENT ON TABLE ai_diagnosis_suggestion IS 'AI辅助诊断建议表';
COMMENT ON COLUMN ai_diagnosis_suggestion.probability IS '疾病概率百分比，如85.50表示85.50%';

-- ============================================================
-- 表: ai_prescription_review (AI 处方审核表)
-- 说明: AI 对医生处方的自动审核结果
-- ============================================================
CREATE TABLE ai_prescription_review (
    id              SERIAL          PRIMARY KEY,
    register_id     INTEGER         NOT NULL,
    prescription_id INTEGER         NOT NULL,
    review_result   VARCHAR(16)     NOT NULL,
    drug_conflict   TEXT            DEFAULT NULL,
    allergy_risk    TEXT            DEFAULT NULL,
    duplicate_drug  TEXT            DEFAULT NULL,
    dosage_check    TEXT            DEFAULT NULL,
    risk_score      INTEGER         NOT NULL DEFAULT 0,
    risk_details    TEXT            DEFAULT NULL,
    doctor_action   VARCHAR(16)     DEFAULT NULL,
    review_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id        VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_review_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_review_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescription(id),

    CONSTRAINT chk_ai_review_result CHECK (review_result IN ('passed', 'warning', 'rejected')),
    CONSTRAINT chk_ai_review_score CHECK (risk_score >= 0 AND risk_score <= 100),
    CONSTRAINT chk_ai_review_action CHECK (doctor_action IS NULL OR doctor_action IN ('accepted', 'overridden'))
);

CREATE INDEX idx_ai_review_register_id ON ai_prescription_review(register_id);
CREATE INDEX idx_ai_review_prescription_id ON ai_prescription_review(prescription_id);
CREATE INDEX idx_ai_review_result ON ai_prescription_review(review_result);

COMMENT ON TABLE ai_prescription_review IS 'AI处方审核表';
COMMENT ON COLUMN ai_prescription_review.risk_score IS '综合风险评分0-100，0无风险，100极高风险';

-- ============================================================
-- 表: ai_follow_up_plan (AI 用药随访计划表)
-- 说明: 患者取药后 AI 自动生成的随访计划
-- ============================================================
CREATE TABLE ai_follow_up_plan (
    id                  SERIAL          PRIMARY KEY,
    register_id         INTEGER         NOT NULL,
    prescription_id     INTEGER         DEFAULT NULL,
    follow_up_day       INTEGER         DEFAULT NULL,
    planned_date        DATE            DEFAULT NULL,
    follow_up_type      VARCHAR(32)     NOT NULL,
    content_template    TEXT            DEFAULT NULL,
    plan_status         VARCHAR(16)     NOT NULL DEFAULT 'pending',
    creation_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    model_id            VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_followup_register
        FOREIGN KEY (register_id) REFERENCES register(id),
    CONSTRAINT fk_ai_followup_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescription(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_ai_followup_type CHECK (follow_up_type IN ('medication', 'side_effect', 'recovery', 'revisit')),
    CONSTRAINT chk_ai_followup_status CHECK (plan_status IN ('pending', 'completed', 'overdue', 'cancelled'))
);

CREATE INDEX idx_ai_followup_register_id ON ai_follow_up_plan(register_id);
CREATE INDEX idx_ai_followup_status ON ai_follow_up_plan(plan_status);
CREATE INDEX idx_ai_followup_planned_date ON ai_follow_up_plan(planned_date);

COMMENT ON TABLE ai_follow_up_plan IS 'AI用药随访计划表';
COMMENT ON COLUMN ai_follow_up_plan.follow_up_type IS '随访类型: medication-用药跟踪, side_effect-副作用反馈, recovery-康复评估, revisit-复诊提醒';

-- ============================================================
-- 第 7 层：AI 随访记录表（依赖第 6 层）
-- ============================================================

-- ============================================================
-- 表: ai_follow_up_record (AI 随访记录表)
-- 说明: 患者用药随访的反馈记录和 AI 康复评估
-- ============================================================
CREATE TABLE ai_follow_up_record (
    id                  SERIAL          PRIMARY KEY,
    follow_up_plan_id   INTEGER         NOT NULL,
    register_id         INTEGER         NOT NULL,
    is_on_time          SMALLINT        DEFAULT NULL,
    side_effect         TEXT            DEFAULT NULL,
    has_side_effect     SMALLINT        DEFAULT NULL,
    symptom_relief      VARCHAR(32)     DEFAULT NULL,
    need_revisit        SMALLINT        DEFAULT NULL,
    patient_feedback    TEXT            DEFAULT NULL,
    ai_assessment       TEXT            DEFAULT NULL,
    ai_advice           TEXT            DEFAULT NULL,
    follow_up_time      TIMESTAMP       DEFAULT NULL,
    model_id            VARCHAR(64)     DEFAULT NULL,

    CONSTRAINT fk_ai_followup_record_plan
        FOREIGN KEY (follow_up_plan_id) REFERENCES ai_follow_up_plan(id),
    CONSTRAINT fk_ai_followup_record_register
        FOREIGN KEY (register_id) REFERENCES register(id),

    CONSTRAINT chk_ai_fur_ontime CHECK (is_on_time IS NULL OR is_on_time IN (0, 1)),
    CONSTRAINT chk_ai_fur_side_effect CHECK (has_side_effect IS NULL OR has_side_effect IN (0, 1)),
    CONSTRAINT chk_ai_fur_relief CHECK (symptom_relief IS NULL OR symptom_relief IN ('relieved', 'partial', 'unchanged', 'worsened')),
    CONSTRAINT chk_ai_fur_revisit CHECK (need_revisit IS NULL OR need_revisit IN (0, 1))
);

CREATE INDEX idx_ai_fur_plan_id ON ai_follow_up_record(follow_up_plan_id);
CREATE INDEX idx_ai_fur_register_id ON ai_follow_up_record(register_id);

COMMENT ON TABLE ai_follow_up_record IS 'AI随访反馈记录表';

-- ============================================================
-- 数据库初始化完成
-- ============================================================

-- ============================================================
-- 演示数据：完整科室、医生、挂号级别配置
-- ============================================================

-- 测试用户数据（用于登录认证）
INSERT INTO users (username, password, real_name, user_type, status) VALUES
    ('admin', 'admin123', '系统管理员', 1, 1),
    ('doctor1', 'doctor123', '张医生', 2, 1),
    ('reg001', 'reg123', '李收费', 3, 1),
    ('medtech01', 'medtech123', '王技师', 4, 1),
    ('pharma01', 'pharma123', '赵药师', 5, 1),
    ('patient001', 'patient123', '患者小明', 6, 1)
ON CONFLICT (username) DO NOTHING;

-- 临床科室（20个）
INSERT INTO department (id, dept_code, dept_name, dept_type) VALUES
    (1, 'NK', '内科', '临床科室'),
    (2, 'HXNK', '呼吸内科', '临床科室'),
    (3, 'XXNK', '心血管内科', '临床科室'),
    (4, 'XHNK', '消化内科', '临床科室'),
    (5, 'SJNK', '神经内科', '临床科室'),
    (6, 'SNK', '肾内科', '临床科室'),
    (7, 'NFMK', '内分泌科', '临床科室'),
    (8, 'WK', '外科', '临床科室'),
    (9, 'GC', '骨科', '临床科室'),
    (10, 'FCHK', '妇产科', '临床科室'),
    (11, 'EK', '儿科', '临床科室'),
    (12, 'XSEK', '新生儿科', '临床科室'),
    (13, 'YFK', '眼科', '临床科室'),
    (14, 'EBHK', '耳鼻咽喉科', '临床科室'),
    (15, 'KQK', '口腔科', '临床科室'),
    (16, 'PFK', '皮肤科', '临床科室'),
    (17, 'ZYK', '中医科', '临床科室'),
    (18, 'ZLK', '肿瘤科', '临床科室'),
    (19, 'JZK', '急诊科', '临床科室'),
    (20, 'KFY', '康复医学科', '临床科室')
ON CONFLICT (dept_code) DO NOTHING;

-- 医技科室（10个）
INSERT INTO department (id, dept_code, dept_name, dept_type) VALUES
    (35, 'FSK', '放射科', '医技科室'),
    (36, 'CSK', '超声科', '医技科室'),
    (37, 'JYK', '检验科', '医技科室'),
    (38, 'YXK', '输血科', '医技科室'),
    (39, 'BLK', '病理科', '医技科室'),
    (40, 'CZK', '处置室', '医技科室'),
    (41, 'NJZX', '内镜中心', '医技科室'),
    (42, 'SS', '手术室', '医技科室'),
    (44, 'XDZX', '消毒供应中心', '医技科室'),
    (45, 'YF', '药房', '医技科室')
ON CONFLICT (dept_code) DO NOTHING;

-- 挂号级别（3级）
INSERT INTO regist_level (id, regist_code, regist_name, regist_fee, regist_quota, sequence_no) VALUES
    (1, 'PT', '普通号', 5.00, 50, 1),
    (2, 'ZJ', '专家号', 15.00, 30, 2),
    (3, 'ZR', '主任医师号', 30.00, 15, 3)
ON CONFLICT (regist_code) DO NOTHING;

-- 排班规则
INSERT INTO scheduling (id, rule_name, week_rule) VALUES
    (1, '周一至周五上午', '1上2上3上4上5上'),
    (2, '周一至周五下午', '1下2下3下4下5下'),
    (3, '周六上午', '6上')
ON CONFLICT (id) DO NOTHING;

-- 结算类别
INSERT INTO settle_category (id, settle_code, settle_name, sequence_no) VALUES
    (1, 'ZF', '自费', 1),
    (2, 'YB', '医保', 2)
ON CONFLICT (settle_code) DO NOTHING;

-- 临床科室医生（20个科室 × 5人 = 100人）
-- 科室1: 内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (1, 1, 1, '内科张医生', 'dev-password'),
    (1, 1, 2, '内科李医生', 'dev-password'),
    (1, 1, 3, '内科王医生', 'dev-password'),
    (1, 2, 1, '内科赵专家', 'dev-password'),
    (1, 3, 1, '内科刘主任', 'dev-password');
-- 科室2: 呼吸内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (2, 1, 1, '呼吸内科周医生', 'dev-password'),
    (2, 1, 2, '呼吸内科吴医生', 'dev-password'),
    (2, 1, 3, '呼吸内科郑医生', 'dev-password'),
    (2, 2, 1, '呼吸内科冯专家', 'dev-password'),
    (2, 3, 1, '呼吸内科陈主任', 'dev-password');
-- 科室3: 心血管内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (3, 1, 1, '心血管内科孙医生', 'dev-password'),
    (3, 1, 2, '心血管内科李医生', 'dev-password'),
    (3, 1, 3, '心血管内科林医生', 'dev-password'),
    (3, 2, 1, '心血管内科何专家', 'dev-password'),
    (3, 3, 1, '心血管内科高主任', 'dev-password');
-- 科室4: 消化内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (4, 1, 1, '消化内科马医生', 'dev-password'),
    (4, 1, 2, '消化内科朱医生', 'dev-password'),
    (4, 1, 3, '消化内科秦医生', 'dev-password'),
    (4, 2, 1, '消化内科尤专家', 'dev-password'),
    (4, 3, 1, '消化内科许主任', 'dev-password');
-- 科室5: 神经内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (5, 1, 1, '神经内科施医生', 'dev-password'),
    (5, 1, 2, '神经内科张医生', 'dev-password'),
    (5, 1, 3, '神经内科蒋医生', 'dev-password'),
    (5, 2, 1, '神经内科韩专家', 'dev-password'),
    (5, 3, 1, '神经内科沈主任', 'dev-password');
-- 科室6: 肾内科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (6, 1, 1, '肾内科唐医生', 'dev-password'),
    (6, 1, 2, '肾内科冯医生', 'dev-password'),
    (6, 1, 3, '肾内科董医生', 'dev-password'),
    (6, 2, 1, '肾内科潘专家', 'dev-password'),
    (6, 3, 1, '肾内科姜主任', 'dev-password');
-- 科室7: 内分泌科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (7, 1, 1, '内分泌科苏医生', 'dev-password'),
    (7, 1, 2, '内分泌科魏医生', 'dev-password'),
    (7, 1, 3, '内分泌科卢医生', 'dev-password'),
    (7, 2, 1, '内分泌科崔专家', 'dev-password'),
    (7, 3, 1, '内分泌科蔡主任', 'dev-password');
-- 科室8: 外科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (8, 1, 1, '外科丁医生', 'dev-password'),
    (8, 1, 2, '外科沈医生', 'dev-password'),
    (8, 1, 3, '外科徐医生', 'dev-password'),
    (8, 2, 1, '外科蒋专家', 'dev-password'),
    (8, 3, 1, '外科沈主任', 'dev-password');
-- 科室9: 骨科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (9, 1, 1, '骨科卢医生', 'dev-password'),
    (9, 1, 2, '骨科马医生', 'dev-password'),
    (9, 1, 3, '骨科龚医生', 'dev-password'),
    (9, 2, 1, '骨科秦专家', 'dev-password'),
    (9, 3, 1, '骨科谢主任', 'dev-password');
-- 科室10: 妇产科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (10, 1, 1, '妇产科苏医生', 'dev-password'),
    (10, 1, 2, '妇产科韦医生', 'dev-password'),
    (10, 1, 3, '妇产科严医生', 'dev-password'),
    (10, 2, 1, '妇产科卫专家', 'dev-password'),
    (10, 3, 1, '妇产科武主任', 'dev-password');
-- 科室11: 儿科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (11, 1, 1, '儿科陶医生', 'dev-password'),
    (11, 1, 2, '儿科俞医生', 'dev-password'),
    (11, 1, 3, '儿科任医生', 'dev-password'),
    (11, 2, 1, '儿科袁专家', 'dev-password'),
    (11, 3, 1, '儿科柳主任', 'dev-password');
-- 科室12: 新生儿科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (12, 1, 1, '新生儿科毕医生', 'dev-password'),
    (12, 1, 2, '新生儿科郝医生', 'dev-password'),
    (12, 1, 3, '新生儿科邬医生', 'dev-password'),
    (12, 2, 1, '新生儿科安专家', 'dev-password'),
    (12, 3, 1, '新生儿科常主任', 'dev-password');
-- 科室13: 眼科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (13, 1, 1, '眼科乐医生', 'dev-password'),
    (13, 1, 2, '眼科于医生', 'dev-password'),
    (13, 1, 3, '眼科傅医生', 'dev-password'),
    (13, 2, 1, '眼科康专家', 'dev-password'),
    (13, 3, 1, '眼科陆主任', 'dev-password');
-- 科室14: 耳鼻咽喉科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (14, 1, 1, '耳鼻咽喉科柴医生', 'dev-password'),
    (14, 1, 2, '耳鼻咽喉科胡医生', 'dev-password'),
    (14, 1, 3, '耳鼻咽喉科戴医生', 'dev-password'),
    (14, 2, 1, '耳鼻咽喉科蔡专家', 'dev-password'),
    (14, 3, 1, '耳鼻咽喉科谭主任', 'dev-password');
-- 科室15: 口腔科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (15, 1, 1, '口腔科舒医生', 'dev-password'),
    (15, 1, 2, '口腔科屈医生', 'dev-password'),
    (15, 1, 3, '口腔科项医生', 'dev-password'),
    (15, 2, 1, '口腔科纪专家', 'dev-password'),
    (15, 3, 1, '口腔科梁主任', 'dev-password');
-- 科室16: 皮肤科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (16, 1, 1, '皮肤科杜医生', 'dev-password'),
    (16, 1, 2, '皮肤科阮医生', 'dev-password'),
    (16, 1, 3, '皮肤科贝医生', 'dev-password'),
    (16, 2, 1, '皮肤科明专家', 'dev-password'),
    (16, 3, 1, '皮肤科程主任', 'dev-password');
-- 科室17: 中医科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (17, 1, 1, '中医科卫医生', 'dev-password'),
    (17, 1, 2, '中医科申医生', 'dev-password'),
    (17, 1, 3, '中医科连医生', 'dev-password'),
    (17, 2, 1, '中医科习专家', 'dev-password'),
    (17, 3, 1, '中医科程主任', 'dev-password');
-- 科室18: 肿瘤科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (18, 1, 1, '肿瘤科向医生', 'dev-password'),
    (18, 1, 2, '肿瘤科丁医生', 'dev-password'),
    (18, 1, 3, '肿瘤科茅医生', 'dev-password'),
    (18, 2, 1, '肿瘤科左专家', 'dev-password'),
    (18, 3, 1, '肿瘤科甘主任', 'dev-password');
-- 科室19: 急诊科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (19, 1, 1, '急诊科龙医生', 'dev-password'),
    (19, 1, 2, '急诊科万医生', 'dev-password'),
    (19, 1, 3, '急诊科柯医生', 'dev-password'),
    (19, 2, 1, '急诊科柯专家', 'dev-password'),
    (19, 3, 1, '急诊科支主任', 'dev-password');
-- 科室20: 康复医学科
INSERT INTO employee (deptment_id, regist_level_id, scheduling_id, realname, password) VALUES
    (20, 1, 1, '康复科管医生', 'dev-password'),
    (20, 1, 2, '康复科蔡医生', 'dev-password'),
    (20, 1, 3, '康复科蒙医生', 'dev-password'),
    (20, 2, 1, '康复科应专家', 'dev-password'),
    (20, 3, 1, '康复科丁主任', 'dev-password');

-- 医技科室人员（不挂号，执行检查/检验）
INSERT INTO employee (deptment_id, realname, password) VALUES
    (35, '放射科技师王一', 'dev-password'),
    (35, '放射科技师李二', 'dev-password'),
    (35, '放射科医生张三', 'dev-password'),
    (36, '超声科技师赵四', 'dev-password'),
    (36, '超声科医生钱五', 'dev-password'),
    (37, '检验科技师孙六', 'dev-password'),
    (37, '检验科医生周七', 'dev-password'),
    (38, '输血科技师吴八', 'dev-password'),
    (39, '病理科医生郑九', 'dev-password'),
    (40, '处置室护士长冯十', 'dev-password'),
    (40, '处置室护士李一', 'dev-password'),
    (41, '内镜中心技师周二', 'dev-password'),
    (41, '内镜中心医生陈二', 'dev-password'),
    (42, '手术室护士长周三', 'dev-password'),
    (42, '手术室麻醉师李三', 'dev-password'),
    (44, '供应中心护士长王四', 'dev-password'),
    (45, '药房药师张五', 'dev-password'),
    (45, '药房药师赵六', 'dev-password');

INSERT INTO disease (id, disease_code, disease_name, diseaseicd, disease_category) VALUES
    (1, 'PJT', '偏头痛', 'G43.9', '神经系统疾病'),
    (2, 'SXDGR', '上呼吸道感染', 'J06.9', '呼吸系统疾病'),
    (3, 'FY', '肺炎', 'J18.9', '呼吸系统疾病')
ON CONFLICT (diseaseicd) DO NOTHING;

INSERT INTO medical_technology (id, tech_code, tech_name, tech_format, tech_price, tech_type, price_type, deptment_id) VALUES
    (1, 'XJCT', '胸部CT', '平扫', 280.00, 'check', '检查费', 3),
    (2, 'TLCT', '头颅CT', '平扫', 260.00, 'check', '检查费', 3),
    (3, 'XCG', '血常规', '全血', 35.00, 'inspection', '检验费', 4),
    (4, 'CRP', 'C反应蛋白', '血清', 45.00, 'inspection', '检验费', 4),
    (5, 'WX', '雾化吸入', '次', 30.00, 'disposal', '处置费', 5)
ON CONFLICT (tech_code) DO NOTHING;

INSERT INTO drug_info (id, drug_code, drug_name, drug_format, drug_unit, manufacturer, drug_dosage, drug_type, drug_price, mnemonic_code) VALUES
    (1, 'ASP001', '阿司匹林肠溶片', '100mg*30片/盒', '盒', '拜耳医药', '片剂', '西药', 25.80, 'ASPL'),
    (2, 'BLF001', '布洛芬缓释胶囊', '0.3g*20粒/盒', '盒', '中美史克', '胶囊', '西药', 18.50, 'BLF'),
    (3, 'AMX001', '阿莫西林胶囊', '0.25g*24粒/盒', '盒', '哈药集团', '胶囊', '西药', 16.00, 'AMXL')
ON CONFLICT (drug_code) DO NOTHING;

INSERT INTO register (
    id, case_number, real_name, gender, birthdate, age, age_type, home_address,
    visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id,
    is_book, regist_method, regist_money, visit_state
) VALUES
    (1001, 'BL20260523001', '张三', '男', '1990-05-15', 36, '年', '沈阳市和平区XX路XX号',
     CURRENT_TIMESTAMP, '上午', 1, 1, 2, 1, '是', '现金', 15.00, 1),
    (1002, 'BL20260523002', '李四', '女', '1988-08-02', 38, '年', '沈阳市沈河区XX街',
     CURRENT_TIMESTAMP, '上午', 1, 1, 2, 1, '否', '微信', 15.00, 2)
ON CONFLICT DO NOTHING;

INSERT INTO ai_consultation_record (
    register_id, round_number, ai_question, patient_answer, consultation_state,
    chief_complaint, symptom_duration, history_summary, allergy_summary,
    medication_summary, ai_summary, suggested_exam, completion_time
) VALUES
    (1001, 3, '是否还有其他不适？', '伴有咳嗽和低热', 'completed',
     '咳嗽、低热3天', '3天', '既往体健，无慢性病史', '青霉素过敏',
     '近3天自行服用退热药，效果一般', '患者咳嗽低热3天，伴轻微胸闷，建议排查呼吸道感染。',
     '建议完善胸部CT、血常规、C反应蛋白检查', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 演示数据：患者档案和家庭成员管理
-- ============================================================

-- 患者档案数据
INSERT INTO patient (id, real_name, id_card, gender, birthdate, phone, relation, is_primary, allergy_history) VALUES
    (1, '患者小明', '210102199001011234', '男', '1990-01-01', '13800138000', '本人', 1, '青霉素过敏'),
    (2, '张小华', '210102196503151234', '女', '1965-03-15', '13900139000', '母亲', 0, '无'),
    (3, '李建国', '210102196203201234', '男', '1962-03-20', '13900139001', '父亲', 0, '磺胺类过敏')
ON CONFLICT (id_card) DO NOTHING;

-- 用户-患者管理关联数据
INSERT INTO user_patient_managed (user_id, patient_id) VALUES
    (6, 1),  -- patient001 管理本人（患者小明）
    (6, 2),  -- patient001 管理母亲（张小华）
    (6, 3)   -- patient001 管理父亲（李建国）
ON CONFLICT DO NOTHING;

-- 更新 users 表关联 patient_id（账号本人关联自己的患者档案）
UPDATE users SET patient_id = 1 WHERE username = 'patient001';
