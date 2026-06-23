--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_consultation_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_consultation_record (
    id integer NOT NULL,
    register_id integer NOT NULL,
    round_number integer DEFAULT 1 NOT NULL,
    ai_question text,
    patient_answer text,
    consultation_state character varying(16) DEFAULT 'in_progress'::character varying NOT NULL,
    chief_complaint character varying(512) DEFAULT NULL::character varying,
    symptom_duration character varying(128) DEFAULT NULL::character varying,
    history_summary text,
    allergy_summary text,
    medication_summary text,
    ai_summary text,
    suggested_exam text,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completion_time timestamp without time zone,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_consult_state CHECK (((consultation_state)::text = ANY ((ARRAY['in_progress'::character varying, 'completed'::character varying, 'cancelled'::character varying])::text[])))
);


--
-- Name: TABLE ai_consultation_record; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_consultation_record IS 'AI预问诊记录表（多轮对话）';


--
-- Name: ai_consultation_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_consultation_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_consultation_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_consultation_record_id_seq OWNED BY public.ai_consultation_record.id;


--
-- Name: ai_diagnosis_suggestion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_diagnosis_suggestion (
    id integer NOT NULL,
    register_id integer NOT NULL,
    disease_id integer,
    disease_name character varying(255) DEFAULT NULL::character varying,
    recommend_icd character varying(64) DEFAULT NULL::character varying,
    probability numeric(5,2) DEFAULT NULL::numeric,
    risk_level character varying(16) DEFAULT 'low'::character varying NOT NULL,
    treatment_direction text,
    diagnosis_basis text,
    is_adopted smallint DEFAULT 0 NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_diagnosis_adopted CHECK ((is_adopted = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_ai_diagnosis_probability CHECK (((probability IS NULL) OR ((probability >= (0)::numeric) AND (probability <= (100)::numeric)))),
    CONSTRAINT chk_ai_diagnosis_risk CHECK (((risk_level)::text = ANY ((ARRAY['low'::character varying, 'medium'::character varying, 'high'::character varying])::text[])))
);


--
-- Name: TABLE ai_diagnosis_suggestion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_diagnosis_suggestion IS 'AI辅助诊断建议表';


--
-- Name: COLUMN ai_diagnosis_suggestion.probability; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_diagnosis_suggestion.probability IS '疾病概率百分比，如85.50表示85.50%';


--
-- Name: ai_diagnosis_suggestion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_diagnosis_suggestion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_diagnosis_suggestion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_diagnosis_suggestion_id_seq OWNED BY public.ai_diagnosis_suggestion.id;


--
-- Name: ai_exam_analysis; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_exam_analysis (
    id integer NOT NULL,
    register_id integer NOT NULL,
    check_request_id integer,
    inspection_request_id integer,
    analysis_type character varying(16) NOT NULL,
    original_result text,
    abnormal_indicators text,
    risk_level character varying(16) DEFAULT 'normal'::character varying NOT NULL,
    analysis_report text,
    correlation_analysis text,
    is_viewed smallint DEFAULT 0 NOT NULL,
    analysis_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_exam_analysis_risk CHECK (((risk_level)::text = ANY ((ARRAY['normal'::character varying, 'attention'::character varying, 'warning'::character varying, 'danger'::character varying])::text[]))),
    CONSTRAINT chk_ai_exam_analysis_type CHECK (((analysis_type)::text = ANY ((ARRAY['check'::character varying, 'inspection'::character varying])::text[]))),
    CONSTRAINT chk_ai_exam_analysis_viewed CHECK ((is_viewed = ANY (ARRAY[0, 1])))
);


--
-- Name: TABLE ai_exam_analysis; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_exam_analysis IS 'AI检查/检验结果分析表';


--
-- Name: COLUMN ai_exam_analysis.abnormal_indicators; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_exam_analysis.abnormal_indicators IS '异常指标，JSON格式，如: [{"指标":"白细胞","值":"15.2","参考值":"4-10","单位":"10^9/L"}]';


--
-- Name: ai_exam_analysis_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_exam_analysis_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_exam_analysis_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_exam_analysis_id_seq OWNED BY public.ai_exam_analysis.id;


--
-- Name: ai_exam_suggestion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_exam_suggestion (
    id integer NOT NULL,
    register_id integer NOT NULL,
    tech_id integer NOT NULL,
    tech_name character varying(64) DEFAULT NULL::character varying,
    suggest_type character varying(16) NOT NULL,
    suggest_reason text,
    priority integer DEFAULT 1 NOT NULL,
    is_adopted smallint DEFAULT 0 NOT NULL,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_exam_sug_adopted CHECK ((is_adopted = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_ai_exam_sug_priority CHECK ((priority = ANY (ARRAY[1, 2, 3]))),
    CONSTRAINT chk_ai_exam_sug_type CHECK (((suggest_type)::text = ANY ((ARRAY['check'::character varying, 'inspection'::character varying])::text[])))
);


--
-- Name: TABLE ai_exam_suggestion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_exam_suggestion IS 'AI检查/检验推荐表';


--
-- Name: ai_exam_suggestion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_exam_suggestion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_exam_suggestion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_exam_suggestion_id_seq OWNED BY public.ai_exam_suggestion.id;


--
-- Name: ai_follow_up_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_follow_up_plan (
    id integer NOT NULL,
    register_id integer NOT NULL,
    prescription_id integer,
    follow_up_day integer,
    planned_date date,
    follow_up_type character varying(32) NOT NULL,
    content_template text,
    plan_status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_followup_status CHECK (((plan_status)::text = ANY ((ARRAY['pending'::character varying, 'completed'::character varying, 'overdue'::character varying, 'cancelled'::character varying])::text[]))),
    CONSTRAINT chk_ai_followup_type CHECK (((follow_up_type)::text = ANY ((ARRAY['medication'::character varying, 'side_effect'::character varying, 'recovery'::character varying, 'revisit'::character varying])::text[])))
);


--
-- Name: TABLE ai_follow_up_plan; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_follow_up_plan IS 'AI用药随访计划表';


--
-- Name: COLUMN ai_follow_up_plan.follow_up_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_follow_up_plan.follow_up_type IS '随访类型: medication-用药跟踪, side_effect-副作用反馈, recovery-康复评估, revisit-复诊提醒';


--
-- Name: ai_follow_up_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_follow_up_plan_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_follow_up_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_follow_up_plan_id_seq OWNED BY public.ai_follow_up_plan.id;


--
-- Name: ai_follow_up_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_follow_up_record (
    id integer NOT NULL,
    follow_up_plan_id integer NOT NULL,
    register_id integer NOT NULL,
    is_on_time smallint,
    side_effect text,
    has_side_effect smallint,
    symptom_relief character varying(32) DEFAULT NULL::character varying,
    need_revisit smallint,
    patient_feedback text,
    ai_assessment text,
    ai_advice text,
    follow_up_time timestamp without time zone,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_fur_ontime CHECK (((is_on_time IS NULL) OR (is_on_time = ANY (ARRAY[0, 1])))),
    CONSTRAINT chk_ai_fur_relief CHECK (((symptom_relief IS NULL) OR ((symptom_relief)::text = ANY ((ARRAY['relieved'::character varying, 'partial'::character varying, 'unchanged'::character varying, 'worsened'::character varying])::text[])))),
    CONSTRAINT chk_ai_fur_revisit CHECK (((need_revisit IS NULL) OR (need_revisit = ANY (ARRAY[0, 1])))),
    CONSTRAINT chk_ai_fur_side_effect CHECK (((has_side_effect IS NULL) OR (has_side_effect = ANY (ARRAY[0, 1]))))
);


--
-- Name: TABLE ai_follow_up_record; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_follow_up_record IS 'AI随访反馈记录表';


--
-- Name: ai_follow_up_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_follow_up_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_follow_up_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_follow_up_record_id_seq OWNED BY public.ai_follow_up_record.id;


--
-- Name: ai_medical_record_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_medical_record_log (
    id integer NOT NULL,
    register_id integer NOT NULL,
    medical_record_id integer,
    source_type character varying(32) NOT NULL,
    ai_readme text,
    ai_present text,
    ai_history text,
    ai_allergy text,
    ai_physique text,
    ai_diagnosis text,
    is_adopted smallint DEFAULT 0 NOT NULL,
    doctor_modification text,
    generation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_mrlog_adopted CHECK ((is_adopted = ANY (ARRAY[0, 1, 2]))),
    CONSTRAINT chk_ai_mrlog_source CHECK (((source_type)::text = ANY ((ARRAY['consultation'::character varying, 'dictation'::character varying, 'exam'::character varying, 'preliminary_diagnosis'::character varying])::text[])))
);


--
-- Name: TABLE ai_medical_record_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_medical_record_log IS 'AI病历生成日志表';


--
-- Name: COLUMN ai_medical_record_log.doctor_modification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_medical_record_log.doctor_modification IS '医生修改内容，JSON格式记录被修改的字段和修改前后值';


--
-- Name: ai_medical_record_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_medical_record_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_medical_record_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_medical_record_log_id_seq OWNED BY public.ai_medical_record_log.id;


--
-- Name: ai_prescription_review; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_prescription_review (
    id integer NOT NULL,
    register_id integer NOT NULL,
    prescription_id integer NOT NULL,
    review_result character varying(16) NOT NULL,
    drug_conflict text,
    allergy_risk text,
    duplicate_drug text,
    dosage_check text,
    risk_score integer DEFAULT 0 NOT NULL,
    risk_details text,
    doctor_action character varying(16) DEFAULT NULL::character varying,
    review_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_review_action CHECK (((doctor_action IS NULL) OR ((doctor_action)::text = ANY ((ARRAY['accepted'::character varying, 'overridden'::character varying])::text[])))),
    CONSTRAINT chk_ai_review_result CHECK (((review_result)::text = ANY ((ARRAY['passed'::character varying, 'warning'::character varying, 'rejected'::character varying])::text[]))),
    CONSTRAINT chk_ai_review_score CHECK (((risk_score >= 0) AND (risk_score <= 100)))
);


--
-- Name: TABLE ai_prescription_review; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_prescription_review IS 'AI处方审核表';


--
-- Name: COLUMN ai_prescription_review.risk_score; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_prescription_review.risk_score IS '综合风险评分0-100，0无风险，100极高风险';


--
-- Name: ai_prescription_review_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_prescription_review_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_prescription_review_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_prescription_review_id_seq OWNED BY public.ai_prescription_review.id;


--
-- Name: ai_triage_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_triage_record (
    id integer NOT NULL,
    patient_name character varying(64) DEFAULT NULL::character varying,
    patient_age integer,
    patient_gender character varying(6) DEFAULT NULL::character varying,
    symptom_description text NOT NULL,
    recommend_dept_id integer,
    recommend_dept_name character varying(64) DEFAULT NULL::character varying,
    recommend_doctor_id integer,
    recommend_doctor_name character varying(64) DEFAULT NULL::character varying,
    risk_level character varying(16) DEFAULT 'normal'::character varying NOT NULL,
    is_priority smallint DEFAULT 0 NOT NULL,
    ai_analysis text,
    register_id integer,
    triage_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    model_id character varying(64) DEFAULT NULL::character varying,
    CONSTRAINT chk_ai_triage_gender CHECK (((patient_gender IS NULL) OR ((patient_gender)::text = ANY ((ARRAY['男'::character varying, '女'::character varying])::text[])))),
    CONSTRAINT chk_ai_triage_priority CHECK ((is_priority = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_ai_triage_risk CHECK (((risk_level)::text = ANY ((ARRAY['normal'::character varying, 'urgent'::character varying, 'critical'::character varying])::text[])))
);


--
-- Name: TABLE ai_triage_record; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.ai_triage_record IS 'AI导诊记录表';


--
-- Name: COLUMN ai_triage_record.register_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.ai_triage_record.register_id IS '关联挂号ID，患者实际挂号后回填';


--
-- Name: ai_triage_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ai_triage_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ai_triage_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ai_triage_record_id_seq OWNED BY public.ai_triage_record.id;


--
-- Name: check_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.check_request (
    id integer NOT NULL,
    register_id integer NOT NULL,
    medical_technology_id integer NOT NULL,
    check_info character varying(512) DEFAULT NULL::character varying,
    check_position character varying(255) DEFAULT NULL::character varying,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    check_employee_id integer,
    inputcheck_employee_id integer,
    check_time timestamp without time zone,
    check_result character varying(512) DEFAULT NULL::character varying,
    check_state character varying(64) DEFAULT '待检查'::character varying NOT NULL,
    check_remark character varying(512) DEFAULT NULL::character varying,
    CONSTRAINT chk_check_request_state CHECK (((check_state)::text = ANY ((ARRAY['待检查'::character varying, '检查中'::character varying, '已完成'::character varying, '已归档'::character varying])::text[])))
);


--
-- Name: TABLE check_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.check_request IS '检查申请表';


--
-- Name: COLUMN check_request.check_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.check_request.check_state IS '状态: 待检查 → 检查中 → 已完成';


--
-- Name: check_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.check_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: check_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.check_request_id_seq OWNED BY public.check_request.id;


--
-- Name: department; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.department (
    id integer NOT NULL,
    dept_code character varying(64) NOT NULL,
    dept_name character varying(64) NOT NULL,
    dept_type character varying(64) DEFAULT NULL::character varying,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_department_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


--
-- Name: TABLE department; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.department IS '科室表';


--
-- Name: COLUMN department.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.department.id IS '主键ID';


--
-- Name: COLUMN department.dept_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.department.dept_code IS '科室编码，如: SJNK(神经内科)';


--
-- Name: COLUMN department.dept_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.department.dept_name IS '科室名称';


--
-- Name: COLUMN department.dept_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.department.dept_type IS '科室类型: 临床科室、医技科室等';


--
-- Name: COLUMN department.delmark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.department.delmark IS '生效标记: 1-正常, 0-已删除';


--
-- Name: department_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.department_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.department_id_seq OWNED BY public.department.id;


--
-- Name: disease; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disease (
    id integer NOT NULL,
    disease_code character varying(64) DEFAULT NULL::character varying,
    disease_name character varying(255) NOT NULL,
    diseaseicd character varying(64) DEFAULT NULL::character varying,
    disease_category character varying(64) DEFAULT NULL::character varying
);


--
-- Name: TABLE disease; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.disease IS '疾病字典表';


--
-- Name: COLUMN disease.diseaseicd; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.disease.diseaseicd IS '国际ICD编码，如: G43.9(偏头痛)';


--
-- Name: disease_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.disease_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disease_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.disease_id_seq OWNED BY public.disease.id;


--
-- Name: disposal_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disposal_request (
    id integer NOT NULL,
    register_id integer NOT NULL,
    medical_technology_id integer NOT NULL,
    disposal_info character varying(512) DEFAULT NULL::character varying,
    disposal_position character varying(255) DEFAULT NULL::character varying,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    disposal_employee_id integer,
    inputdisposal_employee_id integer,
    disposal_time timestamp without time zone,
    disposal_result character varying(512) DEFAULT NULL::character varying,
    disposal_state character varying(64) DEFAULT '待处置'::character varying NOT NULL,
    disposal_remark character varying(512) DEFAULT NULL::character varying,
    CONSTRAINT chk_disposal_state CHECK (((disposal_state)::text = ANY ((ARRAY['待处置'::character varying, '处置中'::character varying, '已完成'::character varying, '已归档'::character varying])::text[])))
);


--
-- Name: TABLE disposal_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.disposal_request IS '处置申请表';


--
-- Name: disposal_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.disposal_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disposal_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.disposal_request_id_seq OWNED BY public.disposal_request.id;


--
-- Name: drug_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.drug_info (
    id integer NOT NULL,
    drug_code character varying(255) NOT NULL,
    drug_name character varying(255) NOT NULL,
    drug_format character varying(255) DEFAULT NULL::character varying,
    drug_unit character varying(16) DEFAULT NULL::character varying,
    manufacturer character varying(255) DEFAULT NULL::character varying,
    drug_dosage character varying(64) DEFAULT NULL::character varying,
    drug_type character varying(64) DEFAULT NULL::character varying,
    drug_price numeric(8,2) DEFAULT 0.00 NOT NULL,
    mnemonic_code character varying(255) DEFAULT NULL::character varying,
    creation_date date DEFAULT CURRENT_DATE,
    CONSTRAINT chk_drug_price CHECK ((drug_price >= (0)::numeric))
);


--
-- Name: TABLE drug_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.drug_info IS '药品信息表';


--
-- Name: COLUMN drug_info.mnemonic_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.drug_info.mnemonic_code IS '拼音助记码，用于快速搜索';


--
-- Name: drug_info_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.drug_info_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: drug_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.drug_info_id_seq OWNED BY public.drug_info.id;


--
-- Name: employee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee (
    id integer NOT NULL,
    deptment_id integer,
    regist_level_id integer,
    scheduling_id integer,
    realname character varying(64) NOT NULL,
    password character varying(64) NOT NULL,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_employee_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


--
-- Name: TABLE employee; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.employee IS '医院员工表';


--
-- Name: COLUMN employee.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.employee.password IS '密码（BCrypt加密存储，禁止明文）';


--
-- Name: employee_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.employee_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: employee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.employee_id_seq OWNED BY public.employee.id;


--
-- Name: inspection_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_request (
    id integer NOT NULL,
    register_id integer NOT NULL,
    medical_technology_id integer NOT NULL,
    inspection_info character varying(512) DEFAULT NULL::character varying,
    inspection_position character varying(255) DEFAULT NULL::character varying,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    inspection_employee_id integer,
    inputinspection_employee_id integer,
    inspection_time timestamp without time zone,
    inspection_result text DEFAULT NULL::character varying,
    inspection_state character varying(64) DEFAULT '待检验'::character varying NOT NULL,
    inspection_remark character varying(512) DEFAULT NULL::character varying,
    CONSTRAINT chk_inspection_state CHECK (((inspection_state)::text = ANY ((ARRAY['待检验'::character varying, '检验中'::character varying, '已完成'::character varying, '已归档'::character varying])::text[])))
);


--
-- Name: TABLE inspection_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.inspection_request IS '检验申请表';


--
-- Name: inspection_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inspection_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inspection_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inspection_request_id_seq OWNED BY public.inspection_request.id;


--
-- Name: medical_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medical_record (
    id integer NOT NULL,
    register_id integer NOT NULL,
    readme character varying(512) DEFAULT NULL::character varying,
    present character varying(512) DEFAULT NULL::character varying,
    present_treat character varying(512) DEFAULT NULL::character varying,
    history character varying(512) DEFAULT NULL::character varying,
    allergy character varying(512) DEFAULT NULL::character varying,
    physique character varying(512) DEFAULT NULL::character varying,
    proposal character varying(512) DEFAULT NULL::character varying,
    careful character varying(512) DEFAULT NULL::character varying,
    diagnosis character varying(512) DEFAULT NULL::character varying,
    cure character varying(512) DEFAULT NULL::character varying,
    preliminary_diagnosis character varying(512)
);


--
-- Name: TABLE medical_record; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.medical_record IS '患者病历表';


--
-- Name: medical_record_disease; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medical_record_disease (
    medical_record_id integer NOT NULL,
    disease_id integer NOT NULL
);


--
-- Name: TABLE medical_record_disease; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.medical_record_disease IS '病历-疾病关联表（多对多）';


--
-- Name: medical_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.medical_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: medical_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.medical_record_id_seq OWNED BY public.medical_record.id;


--
-- Name: medical_technology; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.medical_technology (
    id integer NOT NULL,
    tech_code character varying(64) NOT NULL,
    tech_name character varying(64) NOT NULL,
    tech_format character varying(64) DEFAULT NULL::character varying,
    tech_price numeric(8,2) DEFAULT 0.00 NOT NULL,
    tech_type character varying(64) NOT NULL,
    price_type character varying(64) DEFAULT NULL::character varying,
    deptment_id integer,
    ai_category_code character varying(64),
    CONSTRAINT chk_medtech_price CHECK ((tech_price >= (0)::numeric)),
    CONSTRAINT chk_medtech_type CHECK (((tech_type)::text = ANY ((ARRAY['check'::character varying, 'inspection'::character varying, 'disposal'::character varying])::text[])))
);


--
-- Name: TABLE medical_technology; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.medical_technology IS '医技项目表（检查/检验/处置统一目录）';


--
-- Name: COLUMN medical_technology.tech_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.medical_technology.tech_type IS '项目类型: check-检查, inspection-检验, disposal-处置';


--
-- Name: medical_technology_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.medical_technology_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: medical_technology_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.medical_technology_id_seq OWNED BY public.medical_technology.id;


--
-- Name: prescription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prescription (
    id integer NOT NULL,
    register_id integer NOT NULL,
    drug_id integer NOT NULL,
    drug_usage character varying(255) DEFAULT NULL::character varying,
    drug_number character varying(255) DEFAULT NULL::character varying,
    creation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    drug_state character varying(64) DEFAULT '未发'::character varying NOT NULL,
    CONSTRAINT chk_prescription_state CHECK (((drug_state)::text = ANY ((ARRAY['未发'::character varying, '已发'::character varying, '已退'::character varying])::text[])))
);


--
-- Name: TABLE prescription; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.prescription IS '处方表（每条记录对应一个药品）';


--
-- Name: COLUMN prescription.drug_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.prescription.drug_state IS '状态: 未发 → 已发，或 未发 → 已退';


--
-- Name: prescription_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.prescription_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: prescription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.prescription_id_seq OWNED BY public.prescription.id;


--
-- Name: regist_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regist_level (
    id integer NOT NULL,
    regist_code character varying(64) NOT NULL,
    regist_name character varying(64) NOT NULL,
    regist_fee numeric(8,2) DEFAULT 0.00 NOT NULL,
    regist_quota integer DEFAULT 0,
    sequence_no integer DEFAULT 0,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_regist_level_delmark CHECK ((delmark = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_regist_level_fee CHECK ((regist_fee >= (0)::numeric)),
    CONSTRAINT chk_regist_level_quota CHECK ((regist_quota >= 0))
);


--
-- Name: TABLE regist_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.regist_level IS '挂号级别表';


--
-- Name: COLUMN regist_level.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.id IS '主键ID';


--
-- Name: COLUMN regist_level.regist_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.regist_code IS '号别编码';


--
-- Name: COLUMN regist_level.regist_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.regist_name IS '号别名称';


--
-- Name: COLUMN regist_level.regist_fee; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.regist_fee IS '挂号费（元）';


--
-- Name: COLUMN regist_level.regist_quota; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.regist_quota IS '每半天挂号限额';


--
-- Name: COLUMN regist_level.sequence_no; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.sequence_no IS '显示顺序号';


--
-- Name: COLUMN regist_level.delmark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regist_level.delmark IS '生效标记: 1-正常, 0-已删除';


--
-- Name: regist_level_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.regist_level_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: regist_level_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.regist_level_id_seq OWNED BY public.regist_level.id;


--
-- Name: register; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.register (
    id integer NOT NULL,
    case_number character varying(64) NOT NULL,
    real_name character varying(64) NOT NULL,
    gender character varying(6) DEFAULT NULL::character varying,
    card_number character varying(18) DEFAULT NULL::character varying,
    birthdate date,
    age integer,
    age_type character varying(6) DEFAULT NULL::character varying,
    home_address character varying(128) DEFAULT NULL::character varying,
    visit_date timestamp without time zone,
    noon character varying(6) DEFAULT NULL::character varying,
    deptment_id integer NOT NULL,
    employee_id integer NOT NULL,
    regist_level_id integer NOT NULL,
    settle_category_id integer,
    is_book character varying(2) DEFAULT '否'::character varying,
    regist_method character varying(10) DEFAULT NULL::character varying,
    regist_money numeric(8,2) DEFAULT 0.00,
    visit_state smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_register_gender CHECK (((gender IS NULL) OR ((gender)::text = ANY ((ARRAY['男'::character varying, '女'::character varying])::text[])))),
    CONSTRAINT chk_register_is_book CHECK (((is_book)::text = ANY ((ARRAY['是'::character varying, '否'::character varying])::text[]))),
    CONSTRAINT chk_register_noon CHECK (((noon IS NULL) OR ((noon)::text = ANY ((ARRAY['上午'::character varying, '下午'::character varying])::text[])))),
    CONSTRAINT chk_register_regist_money CHECK ((regist_money >= (0)::numeric)),
    CONSTRAINT chk_register_visit_state CHECK ((visit_state = ANY (ARRAY[1, 2, 3, 4, 5, 6])))
);


--
-- Name: TABLE register; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.register IS '患者历次挂号信息表';


--
-- Name: COLUMN register.case_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.register.case_number IS '病历号，同一患者多次挂号共用同一病历号';


--
-- Name: COLUMN register.visit_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号, 5-检查检验中, 6-检查检验完成';


--
-- Name: register_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.register_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: register_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.register_id_seq OWNED BY public.register.id;


--
-- Name: result_form_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_form_category (
    category_code character varying(64) NOT NULL,
    category_name character varying(128) NOT NULL,
    description character varying(512) DEFAULT NULL::character varying
);


--
-- Name: result_form_field; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.result_form_field (
    id integer NOT NULL,
    owner_type character varying(32) NOT NULL,
    owner_key character varying(64) NOT NULL,
    field_key character varying(64) NOT NULL,
    field_label character varying(128) NOT NULL,
    field_type character varying(32) DEFAULT 'textarea'::character varying NOT NULL,
    required boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    placeholder character varying(255) DEFAULT NULL::character varying,
    max_length integer,
    options_json text,
    CONSTRAINT chk_result_form_field_owner_type CHECK (((owner_type)::text = ANY ((ARRAY['category'::character varying, 'tech_extension'::character varying])::text[]))),
    CONSTRAINT chk_result_form_field_type CHECK (((field_type)::text = ANY ((ARRAY['text'::character varying, 'textarea'::character varying, 'number'::character varying])::text[])))
);


--
-- Name: result_form_field_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.result_form_field_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: result_form_field_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.result_form_field_id_seq OWNED BY public.result_form_field.id;


--
-- Name: scheduling; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scheduling (
    id integer NOT NULL,
    rule_name character varying(64) NOT NULL,
    week_rule character varying(14) DEFAULT NULL::character varying,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_scheduling_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


--
-- Name: TABLE scheduling; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.scheduling IS '排班规则表';


--
-- Name: COLUMN scheduling.week_rule; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.scheduling.week_rule IS '星期规则，格式: 数字+午别，如 1上3上5上 表示周一三五上午';


--
-- Name: scheduling_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.scheduling_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: scheduling_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.scheduling_id_seq OWNED BY public.scheduling.id;


--
-- Name: settle_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settle_category (
    id integer NOT NULL,
    settle_code character varying(64) NOT NULL,
    settle_name character varying(64) NOT NULL,
    sequence_no integer DEFAULT 0,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_settle_category_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


--
-- Name: TABLE settle_category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.settle_category IS '结算类别表';


--
-- Name: settle_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.settle_category_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: settle_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.settle_category_id_seq OWNED BY public.settle_category.id;


--
-- Name: ai_consultation_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_consultation_record ALTER COLUMN id SET DEFAULT nextval('public.ai_consultation_record_id_seq'::regclass);


--
-- Name: ai_diagnosis_suggestion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion ALTER COLUMN id SET DEFAULT nextval('public.ai_diagnosis_suggestion_id_seq'::regclass);


--
-- Name: ai_exam_analysis id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_analysis ALTER COLUMN id SET DEFAULT nextval('public.ai_exam_analysis_id_seq'::regclass);


--
-- Name: ai_exam_suggestion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_suggestion ALTER COLUMN id SET DEFAULT nextval('public.ai_exam_suggestion_id_seq'::regclass);


--
-- Name: ai_follow_up_plan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_plan ALTER COLUMN id SET DEFAULT nextval('public.ai_follow_up_plan_id_seq'::regclass);


--
-- Name: ai_follow_up_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_record ALTER COLUMN id SET DEFAULT nextval('public.ai_follow_up_record_id_seq'::regclass);


--
-- Name: ai_medical_record_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_medical_record_log ALTER COLUMN id SET DEFAULT nextval('public.ai_medical_record_log_id_seq'::regclass);


--
-- Name: ai_prescription_review id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_prescription_review ALTER COLUMN id SET DEFAULT nextval('public.ai_prescription_review_id_seq'::regclass);


--
-- Name: ai_triage_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_triage_record ALTER COLUMN id SET DEFAULT nextval('public.ai_triage_record_id_seq'::regclass);


--
-- Name: check_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request ALTER COLUMN id SET DEFAULT nextval('public.check_request_id_seq'::regclass);


--
-- Name: department id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.department ALTER COLUMN id SET DEFAULT nextval('public.department_id_seq'::regclass);


--
-- Name: disease id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disease ALTER COLUMN id SET DEFAULT nextval('public.disease_id_seq'::regclass);


--
-- Name: disposal_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request ALTER COLUMN id SET DEFAULT nextval('public.disposal_request_id_seq'::regclass);


--
-- Name: drug_info id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drug_info ALTER COLUMN id SET DEFAULT nextval('public.drug_info_id_seq'::regclass);


--
-- Name: employee id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee ALTER COLUMN id SET DEFAULT nextval('public.employee_id_seq'::regclass);


--
-- Name: inspection_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request ALTER COLUMN id SET DEFAULT nextval('public.inspection_request_id_seq'::regclass);


--
-- Name: medical_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record ALTER COLUMN id SET DEFAULT nextval('public.medical_record_id_seq'::regclass);


--
-- Name: medical_technology id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_technology ALTER COLUMN id SET DEFAULT nextval('public.medical_technology_id_seq'::regclass);


--
-- Name: prescription id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription ALTER COLUMN id SET DEFAULT nextval('public.prescription_id_seq'::regclass);


--
-- Name: regist_level id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regist_level ALTER COLUMN id SET DEFAULT nextval('public.regist_level_id_seq'::regclass);


--
-- Name: register id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register ALTER COLUMN id SET DEFAULT nextval('public.register_id_seq'::regclass);


--
-- Name: result_form_field id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_form_field ALTER COLUMN id SET DEFAULT nextval('public.result_form_field_id_seq'::regclass);


--
-- Name: scheduling id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scheduling ALTER COLUMN id SET DEFAULT nextval('public.scheduling_id_seq'::regclass);


--
-- Name: settle_category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settle_category ALTER COLUMN id SET DEFAULT nextval('public.settle_category_id_seq'::regclass);


--
-- Name: ai_consultation_record ai_consultation_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_consultation_record
    ADD CONSTRAINT ai_consultation_record_pkey PRIMARY KEY (id);


--
-- Name: ai_diagnosis_suggestion ai_diagnosis_suggestion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT ai_diagnosis_suggestion_pkey PRIMARY KEY (id);


--
-- Name: ai_exam_analysis ai_exam_analysis_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT ai_exam_analysis_pkey PRIMARY KEY (id);


--
-- Name: ai_exam_suggestion ai_exam_suggestion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT ai_exam_suggestion_pkey PRIMARY KEY (id);


--
-- Name: ai_follow_up_plan ai_follow_up_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT ai_follow_up_plan_pkey PRIMARY KEY (id);


--
-- Name: ai_follow_up_record ai_follow_up_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT ai_follow_up_record_pkey PRIMARY KEY (id);


--
-- Name: ai_medical_record_log ai_medical_record_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT ai_medical_record_log_pkey PRIMARY KEY (id);


--
-- Name: ai_prescription_review ai_prescription_review_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT ai_prescription_review_pkey PRIMARY KEY (id);


--
-- Name: ai_triage_record ai_triage_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT ai_triage_record_pkey PRIMARY KEY (id);


--
-- Name: check_request check_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT check_request_pkey PRIMARY KEY (id);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: disease disease_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disease
    ADD CONSTRAINT disease_pkey PRIMARY KEY (id);


--
-- Name: disposal_request disposal_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT disposal_request_pkey PRIMARY KEY (id);


--
-- Name: drug_info drug_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drug_info
    ADD CONSTRAINT drug_info_pkey PRIMARY KEY (id);


--
-- Name: employee employee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT employee_pkey PRIMARY KEY (id);


--
-- Name: inspection_request inspection_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT inspection_request_pkey PRIMARY KEY (id);


--
-- Name: medical_record_disease medical_record_disease_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT medical_record_disease_pkey PRIMARY KEY (medical_record_id, disease_id);


--
-- Name: medical_record medical_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT medical_record_pkey PRIMARY KEY (id);


--
-- Name: medical_technology medical_technology_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT medical_technology_pkey PRIMARY KEY (id);


--
-- Name: prescription prescription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT prescription_pkey PRIMARY KEY (id);


--
-- Name: regist_level regist_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regist_level
    ADD CONSTRAINT regist_level_pkey PRIMARY KEY (id);


--
-- Name: register register_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT register_pkey PRIMARY KEY (id);


--
-- Name: result_form_category result_form_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_form_category
    ADD CONSTRAINT result_form_category_pkey PRIMARY KEY (category_code);


--
-- Name: result_form_field result_form_field_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_form_field
    ADD CONSTRAINT result_form_field_pkey PRIMARY KEY (id);


--
-- Name: scheduling scheduling_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scheduling
    ADD CONSTRAINT scheduling_pkey PRIMARY KEY (id);


--
-- Name: settle_category settle_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settle_category
    ADD CONSTRAINT settle_category_pkey PRIMARY KEY (id);


--
-- Name: department uk_department_dept_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT uk_department_dept_code UNIQUE (dept_code);


--
-- Name: disease uk_disease_icd; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disease
    ADD CONSTRAINT uk_disease_icd UNIQUE (diseaseicd);


--
-- Name: drug_info uk_drug_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.drug_info
    ADD CONSTRAINT uk_drug_code UNIQUE (drug_code);


--
-- Name: medical_record uk_medical_record_register; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT uk_medical_record_register UNIQUE (register_id);


--
-- Name: medical_technology uk_medtech_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT uk_medtech_code UNIQUE (tech_code);


--
-- Name: regist_level uk_regist_level_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regist_level
    ADD CONSTRAINT uk_regist_level_code UNIQUE (regist_code);


--
-- Name: result_form_field uk_result_form_field_owner_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.result_form_field
    ADD CONSTRAINT uk_result_form_field_owner_key UNIQUE (owner_type, owner_key, field_key);


--
-- Name: settle_category uk_settle_category_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settle_category
    ADD CONSTRAINT uk_settle_category_code UNIQUE (settle_code);


--
-- Name: idx_ai_consult_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_consult_register_id ON public.ai_consultation_record USING btree (register_id);


--
-- Name: idx_ai_consult_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_consult_state ON public.ai_consultation_record USING btree (consultation_state);


--
-- Name: idx_ai_diagnosis_disease_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_disease_id ON public.ai_diagnosis_suggestion USING btree (disease_id);


--
-- Name: idx_ai_diagnosis_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_diagnosis_register_id ON public.ai_diagnosis_suggestion USING btree (register_id);


--
-- Name: idx_ai_exam_analysis_check_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_exam_analysis_check_id ON public.ai_exam_analysis USING btree (check_request_id);


--
-- Name: idx_ai_exam_analysis_inspection_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_exam_analysis_inspection_id ON public.ai_exam_analysis USING btree (inspection_request_id);


--
-- Name: idx_ai_exam_analysis_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_exam_analysis_register_id ON public.ai_exam_analysis USING btree (register_id);


--
-- Name: idx_ai_exam_analysis_risk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_exam_analysis_risk ON public.ai_exam_analysis USING btree (risk_level);


--
-- Name: idx_ai_exam_sug_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_exam_sug_register_id ON public.ai_exam_suggestion USING btree (register_id);


--
-- Name: idx_ai_followup_planned_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_followup_planned_date ON public.ai_follow_up_plan USING btree (planned_date);


--
-- Name: idx_ai_followup_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_followup_register_id ON public.ai_follow_up_plan USING btree (register_id);


--
-- Name: idx_ai_followup_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_followup_status ON public.ai_follow_up_plan USING btree (plan_status);


--
-- Name: idx_ai_fur_plan_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_fur_plan_id ON public.ai_follow_up_record USING btree (follow_up_plan_id);


--
-- Name: idx_ai_fur_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_fur_register_id ON public.ai_follow_up_record USING btree (register_id);


--
-- Name: idx_ai_mrlog_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_mrlog_register_id ON public.ai_medical_record_log USING btree (register_id);


--
-- Name: idx_ai_review_prescription_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_review_prescription_id ON public.ai_prescription_review USING btree (prescription_id);


--
-- Name: idx_ai_review_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_review_register_id ON public.ai_prescription_review USING btree (register_id);


--
-- Name: idx_ai_review_result; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_review_result ON public.ai_prescription_review USING btree (review_result);


--
-- Name: idx_ai_triage_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_triage_register_id ON public.ai_triage_record USING btree (register_id);


--
-- Name: idx_ai_triage_risk_level; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_triage_risk_level ON public.ai_triage_record USING btree (risk_level);


--
-- Name: idx_ai_triage_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ai_triage_time ON public.ai_triage_record USING btree (triage_time);


--
-- Name: idx_check_request_medtech_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_check_request_medtech_id ON public.check_request USING btree (medical_technology_id);


--
-- Name: idx_check_request_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_check_request_register_id ON public.check_request USING btree (register_id);


--
-- Name: idx_check_request_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_check_request_state ON public.check_request USING btree (check_state);


--
-- Name: idx_disease_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disease_category ON public.disease USING btree (disease_category);


--
-- Name: idx_disease_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disease_name ON public.disease USING btree (disease_name);


--
-- Name: idx_disposal_request_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disposal_request_register_id ON public.disposal_request USING btree (register_id);


--
-- Name: idx_disposal_request_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disposal_request_state ON public.disposal_request USING btree (disposal_state);


--
-- Name: idx_drug_mnemonic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_drug_mnemonic ON public.drug_info USING btree (mnemonic_code);


--
-- Name: idx_drug_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_drug_name ON public.drug_info USING btree (drug_name);


--
-- Name: idx_drug_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_drug_type ON public.drug_info USING btree (drug_type);


--
-- Name: idx_employee_deptment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employee_deptment_id ON public.employee USING btree (deptment_id);


--
-- Name: idx_employee_regist_level_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_employee_regist_level_id ON public.employee USING btree (regist_level_id);


--
-- Name: idx_inspection_request_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_request_register_id ON public.inspection_request USING btree (register_id);


--
-- Name: idx_inspection_request_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inspection_request_state ON public.inspection_request USING btree (inspection_state);


--
-- Name: idx_medtech_deptment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_medtech_deptment_id ON public.medical_technology USING btree (deptment_id);


--
-- Name: idx_medtech_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_medtech_type ON public.medical_technology USING btree (tech_type);


--
-- Name: idx_prescription_drug_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prescription_drug_id ON public.prescription USING btree (drug_id);


--
-- Name: idx_prescription_register_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prescription_register_id ON public.prescription USING btree (register_id);


--
-- Name: idx_prescription_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_prescription_state ON public.prescription USING btree (drug_state);


--
-- Name: idx_register_case_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_case_number ON public.register USING btree (case_number);


--
-- Name: idx_register_deptment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_deptment_id ON public.register USING btree (deptment_id);


--
-- Name: idx_register_employee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_employee_id ON public.register USING btree (employee_id);


--
-- Name: idx_register_real_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_real_name ON public.register USING btree (real_name);


--
-- Name: idx_register_visit_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_visit_date ON public.register USING btree (visit_date);


--
-- Name: idx_register_visit_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_register_visit_state ON public.register USING btree (visit_state);


--
-- Name: idx_result_form_field_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_form_field_owner ON public.result_form_field USING btree (owner_type, owner_key);


--
-- Name: ai_consultation_record fk_ai_consult_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_consultation_record
    ADD CONSTRAINT fk_ai_consult_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_diagnosis_suggestion fk_ai_diagnosis_disease; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT fk_ai_diagnosis_disease FOREIGN KEY (disease_id) REFERENCES public.disease(id) ON DELETE SET NULL;


--
-- Name: ai_diagnosis_suggestion fk_ai_diagnosis_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT fk_ai_diagnosis_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_check; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_check FOREIGN KEY (check_request_id) REFERENCES public.check_request(id) ON DELETE SET NULL;


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_inspection; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_inspection FOREIGN KEY (inspection_request_id) REFERENCES public.inspection_request(id) ON DELETE SET NULL;


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_exam_suggestion fk_ai_exam_sug_medtech; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT fk_ai_exam_sug_medtech FOREIGN KEY (tech_id) REFERENCES public.medical_technology(id);


--
-- Name: ai_exam_suggestion fk_ai_exam_sug_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT fk_ai_exam_sug_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_follow_up_plan fk_ai_followup_prescription; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT fk_ai_followup_prescription FOREIGN KEY (prescription_id) REFERENCES public.prescription(id) ON DELETE SET NULL;


--
-- Name: ai_follow_up_record fk_ai_followup_record_plan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT fk_ai_followup_record_plan FOREIGN KEY (follow_up_plan_id) REFERENCES public.ai_follow_up_plan(id);


--
-- Name: ai_follow_up_record fk_ai_followup_record_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT fk_ai_followup_record_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_follow_up_plan fk_ai_followup_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT fk_ai_followup_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_medical_record_log fk_ai_mrlog_medical_record; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT fk_ai_mrlog_medical_record FOREIGN KEY (medical_record_id) REFERENCES public.medical_record(id) ON DELETE SET NULL;


--
-- Name: ai_medical_record_log fk_ai_mrlog_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT fk_ai_mrlog_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_prescription_review fk_ai_review_prescription; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT fk_ai_review_prescription FOREIGN KEY (prescription_id) REFERENCES public.prescription(id);


--
-- Name: ai_prescription_review fk_ai_review_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT fk_ai_review_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_triage_record fk_ai_triage_dept; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_dept FOREIGN KEY (recommend_dept_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: ai_triage_record fk_ai_triage_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_doctor FOREIGN KEY (recommend_doctor_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: ai_triage_record fk_ai_triage_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_register FOREIGN KEY (register_id) REFERENCES public.register(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_employee FOREIGN KEY (check_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_input_employee FOREIGN KEY (inputcheck_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: check_request fk_check_request_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: disposal_request fk_disposal_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_employee FOREIGN KEY (disposal_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: disposal_request fk_disposal_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_input_employee FOREIGN KEY (inputdisposal_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: disposal_request fk_disposal_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: disposal_request fk_disposal_request_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: employee fk_employee_department; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT fk_employee_department FOREIGN KEY (deptment_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: employee fk_employee_regist_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT fk_employee_regist_level FOREIGN KEY (regist_level_id) REFERENCES public.regist_level(id) ON DELETE SET NULL;


--
-- Name: employee fk_employee_scheduling; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT fk_employee_scheduling FOREIGN KEY (scheduling_id) REFERENCES public.scheduling(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_employee FOREIGN KEY (inspection_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_input_employee FOREIGN KEY (inputinspection_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: inspection_request fk_inspection_request_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: medical_record fk_medical_record_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT fk_medical_record_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: medical_technology fk_medtech_department; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT fk_medtech_department FOREIGN KEY (deptment_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: medical_record_disease fk_mrd_disease; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT fk_mrd_disease FOREIGN KEY (disease_id) REFERENCES public.disease(id);


--
-- Name: medical_record_disease fk_mrd_medical_record; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT fk_mrd_medical_record FOREIGN KEY (medical_record_id) REFERENCES public.medical_record(id) ON DELETE CASCADE;


--
-- Name: prescription fk_prescription_drug; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT fk_prescription_drug FOREIGN KEY (drug_id) REFERENCES public.drug_info(id);


--
-- Name: prescription fk_prescription_register; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT fk_prescription_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: register fk_register_department; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_department FOREIGN KEY (deptment_id) REFERENCES public.department(id);


--
-- Name: register fk_register_employee; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_employee FOREIGN KEY (employee_id) REFERENCES public.employee(id);


--
-- Name: register fk_register_regist_level; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_regist_level FOREIGN KEY (regist_level_id) REFERENCES public.regist_level(id);


--
-- Name: register fk_register_settle_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_settle_category FOREIGN KEY (settle_category_id) REFERENCES public.settle_category(id) ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--


