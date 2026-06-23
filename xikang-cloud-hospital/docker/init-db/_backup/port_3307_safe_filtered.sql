--
-- PostgreSQL database cluster dump
--

\restrict QxiQegxMrdOWtJaNpjSafaSDPFZ9HiMXWUj6yAOKiniwI7Olcmnyc4IxXmj2TS0

SET default_transaction_read_only = off;

SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;

--
-- Drop databases (except postgres and template1)
--

DROP DATABASE IF EXISTS xikang_hospital;




--
-- Drop roles
--



--
-- Roles
--


--
-- User Configurations
--








\unrestrict QxiQegxMrdOWtJaNpjSafaSDPFZ9HiMXWUj6yAOKiniwI7Olcmnyc4IxXmj2TS0

--
-- Databases
--

--
-- Database "template1" dump
--

--
-- PostgreSQL database dump
--

\restrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK

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

--
-- Name: template1; Type: DATABASE; Schema: -; Owner: postgres
--




\unrestrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK
\connect template1
\restrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK

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

--
-- Name: DATABASE template1; Type: COMMENT; Schema: -; Owner: postgres
--



--
-- Name: template1; Type: DATABASE PROPERTIES; Schema: -; Owner: postgres
--



\unrestrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK
\connect template1
\restrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK

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

--
-- Name: DATABASE template1; Type: ACL; Schema: -; Owner: postgres
--

REVOKE CONNECT,TEMPORARY ON DATABASE template1 FROM PUBLIC;
GRANT CONNECT ON DATABASE template1 TO PUBLIC;


--
-- PostgreSQL database dump complete
--

\unrestrict nZcAWHdCRlFeAjSXEl61M4rf7TiStBFWs2WKeAZKJHpA6IZ98f38EkbsvPXTAPK

--
-- Database "postgres" dump
--

--
-- PostgreSQL database dump
--

\restrict EK6tQdxi3kNB5c9gtbU1xwRSmhdQdb09s4w5QgaeBz0B3QFcdAOQc7NfIZsARxO

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

--
-- Name: postgres; Type: DATABASE; Schema: -; Owner: postgres
--




\unrestrict EK6tQdxi3kNB5c9gtbU1xwRSmhdQdb09s4w5QgaeBz0B3QFcdAOQc7NfIZsARxO
\connect postgres
\restrict EK6tQdxi3kNB5c9gtbU1xwRSmhdQdb09s4w5QgaeBz0B3QFcdAOQc7NfIZsARxO

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

--
-- Name: DATABASE postgres; Type: COMMENT; Schema: -; Owner: postgres
--



--
-- PostgreSQL database dump complete
--

\unrestrict EK6tQdxi3kNB5c9gtbU1xwRSmhdQdb09s4w5QgaeBz0B3QFcdAOQc7NfIZsARxO

--
-- Database "xikang_hospital" dump
--

--
-- PostgreSQL database dump
--

\restrict Y29JjHc3qShKGlLuMHvxJOD7SSqFNVOtbDrct4LIzs8mJLx2RAqsOUHUTYt27Li

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

--
-- Name: xikang_hospital; Type: DATABASE; Schema: -; Owner: postgres
--

CREATE DATABASE xikang_hospital WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


ALTER DATABASE xikang_hospital OWNER TO postgres;

\unrestrict Y29JjHc3qShKGlLuMHvxJOD7SSqFNVOtbDrct4LIzs8mJLx2RAqsOUHUTYt27Li
\connect xikang_hospital
\restrict Y29JjHc3qShKGlLuMHvxJOD7SSqFNVOtbDrct4LIzs8mJLx2RAqsOUHUTYt27Li

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
-- Name: ai_consultation_record; Type: TABLE; Schema: public; Owner: postgres
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
    patient_id integer,
    session_uuid character varying(64) DEFAULT NULL::character varying,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ai_consult_state CHECK (((consultation_state)::text = ANY ((ARRAY['in_progress'::character varying, 'completed'::character varying, 'cancelled'::character varying])::text[])))
);


ALTER TABLE public.ai_consultation_record OWNER TO postgres;

--
-- Name: TABLE ai_consultation_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_consultation_record IS 'AI预问诊记录表';


--
-- Name: ai_consultation_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_consultation_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_consultation_record_id_seq OWNER TO postgres;

--
-- Name: ai_consultation_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_consultation_record_id_seq OWNED BY public.ai_consultation_record.id;


--
-- Name: ai_diagnosis_suggestion; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_diagnosis_suggestion OWNER TO postgres;

--
-- Name: TABLE ai_diagnosis_suggestion; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_diagnosis_suggestion IS 'AI辅助诊断建议表';


--
-- Name: ai_diagnosis_suggestion_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_diagnosis_suggestion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_diagnosis_suggestion_id_seq OWNER TO postgres;

--
-- Name: ai_diagnosis_suggestion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_diagnosis_suggestion_id_seq OWNED BY public.ai_diagnosis_suggestion.id;


--
-- Name: ai_exam_analysis; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_exam_analysis OWNER TO postgres;

--
-- Name: TABLE ai_exam_analysis; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_exam_analysis IS 'AI检查/检验结果分析表';


--
-- Name: ai_exam_analysis_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_exam_analysis_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_exam_analysis_id_seq OWNER TO postgres;

--
-- Name: ai_exam_analysis_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_exam_analysis_id_seq OWNED BY public.ai_exam_analysis.id;


--
-- Name: ai_exam_suggestion; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_exam_suggestion OWNER TO postgres;

--
-- Name: TABLE ai_exam_suggestion; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_exam_suggestion IS 'AI检查/检验推荐表';


--
-- Name: ai_exam_suggestion_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_exam_suggestion_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_exam_suggestion_id_seq OWNER TO postgres;

--
-- Name: ai_exam_suggestion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_exam_suggestion_id_seq OWNED BY public.ai_exam_suggestion.id;


--
-- Name: ai_follow_up_plan; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_follow_up_plan OWNER TO postgres;

--
-- Name: TABLE ai_follow_up_plan; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_follow_up_plan IS 'AI用药随访计划表';


--
-- Name: ai_follow_up_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_follow_up_plan_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_follow_up_plan_id_seq OWNER TO postgres;

--
-- Name: ai_follow_up_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_follow_up_plan_id_seq OWNED BY public.ai_follow_up_plan.id;


--
-- Name: ai_follow_up_record; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_follow_up_record OWNER TO postgres;

--
-- Name: TABLE ai_follow_up_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_follow_up_record IS 'AI随访反馈记录表';


--
-- Name: ai_follow_up_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_follow_up_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_follow_up_record_id_seq OWNER TO postgres;

--
-- Name: ai_follow_up_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_follow_up_record_id_seq OWNED BY public.ai_follow_up_record.id;


--
-- Name: ai_medical_record_log; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_medical_record_log OWNER TO postgres;

--
-- Name: TABLE ai_medical_record_log; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_medical_record_log IS 'AI病历生成日志表';


--
-- Name: ai_medical_record_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_medical_record_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_medical_record_log_id_seq OWNER TO postgres;

--
-- Name: ai_medical_record_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_medical_record_log_id_seq OWNED BY public.ai_medical_record_log.id;


--
-- Name: ai_prescription_review; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_prescription_review OWNER TO postgres;

--
-- Name: TABLE ai_prescription_review; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_prescription_review IS 'AI处方审核表';


--
-- Name: ai_prescription_review_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_prescription_review_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_prescription_review_id_seq OWNER TO postgres;

--
-- Name: ai_prescription_review_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_prescription_review_id_seq OWNED BY public.ai_prescription_review.id;


--
-- Name: ai_triage_record; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.ai_triage_record OWNER TO postgres;

--
-- Name: TABLE ai_triage_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.ai_triage_record IS 'AI导诊记录表';


--
-- Name: ai_triage_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.ai_triage_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.ai_triage_record_id_seq OWNER TO postgres;

--
-- Name: ai_triage_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.ai_triage_record_id_seq OWNED BY public.ai_triage_record.id;


--
-- Name: check_request; Type: TABLE; Schema: public; Owner: postgres
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
    check_result text DEFAULT NULL::character varying,
    check_state character varying(64) DEFAULT '待检查'::character varying NOT NULL,
    check_remark character varying(512) DEFAULT NULL::character varying,
    CONSTRAINT chk_check_request_state CHECK (((check_state)::text = ANY ((ARRAY['待检查'::character varying, '检查中'::character varying, '已完成'::character varying, '已归档'::character varying])::text[])))
);


ALTER TABLE public.check_request OWNER TO postgres;

--
-- Name: TABLE check_request; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.check_request IS '检查申请表';


--
-- Name: COLUMN check_request.check_state; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.check_request.check_state IS '状态: 待检查 → 检查中 → 已完成 / 已归档';


--
-- Name: check_request_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.check_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.check_request_id_seq OWNER TO postgres;

--
-- Name: check_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.check_request_id_seq OWNED BY public.check_request.id;


--
-- Name: department; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.department (
    id integer NOT NULL,
    dept_code character varying(64) NOT NULL,
    dept_name character varying(64) NOT NULL,
    dept_type character varying(64) DEFAULT NULL::character varying,
    dept_description text,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_department_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


ALTER TABLE public.department OWNER TO postgres;

--
-- Name: TABLE department; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.department IS '科室表';


--
-- Name: COLUMN department.dept_code; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.department.dept_code IS '科室编码';


--
-- Name: COLUMN department.dept_name; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.department.dept_name IS '科室名称';


--
-- Name: COLUMN department.dept_type; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.department.dept_type IS '科室类型: 临床科室/医技科室';


--
-- Name: COLUMN department.dept_description; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.department.dept_description IS '科室简介';


--
-- Name: COLUMN department.delmark; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.department.delmark IS '软删除标记: 0-有效, 1-已删除';


--
-- Name: department_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.department_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.department_id_seq OWNER TO postgres;

--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.department_id_seq OWNED BY public.department.id;


--
-- Name: disease; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.disease (
    id integer NOT NULL,
    disease_code character varying(64) DEFAULT NULL::character varying,
    disease_name character varying(255) NOT NULL,
    diseaseicd character varying(64) DEFAULT NULL::character varying,
    disease_category character varying(64) DEFAULT NULL::character varying
);


ALTER TABLE public.disease OWNER TO postgres;

--
-- Name: TABLE disease; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.disease IS '疾病字典表';


--
-- Name: COLUMN disease.diseaseicd; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.disease.diseaseicd IS '国际ICD编码';


--
-- Name: disease_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.disease_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.disease_id_seq OWNER TO postgres;

--
-- Name: disease_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.disease_id_seq OWNED BY public.disease.id;


--
-- Name: disposal_request; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.disposal_request OWNER TO postgres;

--
-- Name: TABLE disposal_request; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.disposal_request IS '处置申请表';


--
-- Name: disposal_request_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.disposal_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.disposal_request_id_seq OWNER TO postgres;

--
-- Name: disposal_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.disposal_request_id_seq OWNED BY public.disposal_request.id;


--
-- Name: doctor_schedule; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.doctor_schedule (
    id integer NOT NULL,
    plan_id integer NOT NULL,
    physician_id integer NOT NULL,
    department_id integer NOT NULL,
    work_date date NOT NULL,
    time_slot character varying(6) NOT NULL,
    regist_level_id integer NOT NULL,
    total_quota integer NOT NULL,
    used_quota integer DEFAULT 0 NOT NULL,
    available_quota integer NOT NULL,
    price numeric(8,2) NOT NULL,
    status character varying(16) DEFAULT '正常'::character varying NOT NULL,
    ai_suggestion character varying(255) DEFAULT NULL::character varying,
    modified boolean DEFAULT false NOT NULL,
    modify_remark character varying(255) DEFAULT NULL::character varying,
    created_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    delmark smallint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_ds_status CHECK (((status)::text = ANY ((ARRAY['正常'::character varying, '停诊'::character varying, '满诊'::character varying, '替班'::character varying])::text[]))),
    CONSTRAINT chk_ds_time_slot CHECK (((time_slot)::text = ANY ((ARRAY['上午'::character varying, '下午'::character varying, '晚上'::character varying])::text[])))
);


ALTER TABLE public.doctor_schedule OWNER TO postgres;

--
-- Name: TABLE doctor_schedule; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.doctor_schedule IS '医生出诊明细表';


--
-- Name: doctor_schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.doctor_schedule_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.doctor_schedule_id_seq OWNER TO postgres;

--
-- Name: doctor_schedule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.doctor_schedule_id_seq OWNED BY public.doctor_schedule.id;


--
-- Name: drug_info; Type: TABLE; Schema: public; Owner: postgres
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
    name character varying(255) DEFAULT NULL::character varying,
    generic_name character varying(255) DEFAULT NULL::character varying,
    brand_name character varying(255) DEFAULT NULL::character varying,
    specification character varying(255) DEFAULT NULL::character varying,
    dosage_form character varying(64) DEFAULT NULL::character varying,
    unit character varying(16) DEFAULT NULL::character varying,
    approval_number character varying(255) DEFAULT NULL::character varying,
    price numeric(8,2) DEFAULT 0.00 NOT NULL,
    stock_quantity integer DEFAULT 100 NOT NULL,
    low_stock_threshold integer DEFAULT 20 NOT NULL,
    storage_conditions character varying(255) DEFAULT NULL::character varying,
    instructions text,
    contraindications text,
    adverse_reactions text,
    status smallint DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_drug_info_price_new CHECK ((price >= (0)::numeric)),
    CONSTRAINT chk_drug_info_status CHECK ((status = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_drug_price CHECK ((drug_price >= (0)::numeric))
);


ALTER TABLE public.drug_info OWNER TO postgres;

--
-- Name: TABLE drug_info; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.drug_info IS '药品信息表';


--
-- Name: COLUMN drug_info.mnemonic_code; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.drug_info.mnemonic_code IS '拼音助记码';


--
-- Name: drug_info_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.drug_info_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.drug_info_id_seq OWNER TO postgres;

--
-- Name: drug_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.drug_info_id_seq OWNED BY public.drug_info.id;


--
-- Name: employee; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employee (
    id integer NOT NULL,
    deptment_id integer,
    regist_level_id integer,
    realname character varying(64) NOT NULL,
    password character varying(64) NOT NULL,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_employee_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


ALTER TABLE public.employee OWNER TO postgres;

--
-- Name: TABLE employee; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.employee IS '医院员工表';


--
-- Name: COLUMN employee.password; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.employee.password IS '密码（BCrypt加密存储）';


--
-- Name: employee_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.employee_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.employee_id_seq OWNER TO postgres;

--
-- Name: employee_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.employee_id_seq OWNED BY public.employee.id;


--
-- Name: expense_record; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.expense_record (
    id bigint NOT NULL,
    register_id integer NOT NULL,
    patient_id integer,
    patient_name character varying(64) DEFAULT NULL::character varying,
    category_id integer,
    category_name character varying(64) DEFAULT NULL::character varying,
    item_id integer,
    item_name character varying(128) DEFAULT NULL::character varying,
    item_code character varying(64) DEFAULT NULL::character varying,
    quantity integer DEFAULT 1,
    unit_price numeric(10,2) DEFAULT 0.00,
    total_amount numeric(10,2) DEFAULT 0.00,
    status smallint DEFAULT 0 NOT NULL,
    pay_time timestamp without time zone,
    refund_time timestamp without time zone,
    operator_id integer,
    operator_name character varying(64) DEFAULT NULL::character varying,
    remark character varying(255) DEFAULT NULL::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_expense_record_amount CHECK (((unit_price >= (0)::numeric) AND (total_amount >= (0)::numeric))),
    CONSTRAINT chk_expense_record_quantity CHECK ((quantity > 0)),
    CONSTRAINT chk_expense_record_status CHECK ((status = ANY (ARRAY[0, 1, 2, 3])))
);


ALTER TABLE public.expense_record OWNER TO postgres;

--
-- Name: TABLE expense_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.expense_record IS '费用记录表';


--
-- Name: COLUMN expense_record.status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expense_record.status IS '费用状态: 0-待缴费, 1-已缴费, 2-已退款, 3-已作废';


--
-- Name: expense_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.expense_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.expense_record_id_seq OWNER TO postgres;

--
-- Name: expense_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.expense_record_id_seq OWNED BY public.expense_record.id;


--
-- Name: inspection_request; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.inspection_request OWNER TO postgres;

--
-- Name: TABLE inspection_request; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.inspection_request IS '检验申请表';


--
-- Name: inspection_request_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.inspection_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.inspection_request_id_seq OWNER TO postgres;

--
-- Name: inspection_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.inspection_request_id_seq OWNED BY public.inspection_request.id;


--
-- Name: leave_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.leave_request (
    id integer NOT NULL,
    physician_id integer NOT NULL,
    leave_date date NOT NULL,
    time_slot character varying(6) DEFAULT NULL::character varying,
    leave_type character varying(32) NOT NULL,
    reason text,
    ai_parsed_date date,
    ai_parsed_slot character varying(6) DEFAULT NULL::character varying,
    ai_confidence numeric(3,2) DEFAULT NULL::numeric,
    status character varying(16) DEFAULT '待审批'::character varying NOT NULL,
    approver_id integer,
    approval_time timestamp without time zone,
    auto_processed boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    delmark smallint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_lr_slot CHECK (((time_slot IS NULL) OR ((time_slot)::text = ANY ((ARRAY['上午'::character varying, '下午'::character varying, '全天'::character varying])::text[])))),
    CONSTRAINT chk_lr_status CHECK (((status)::text = ANY ((ARRAY['待审批'::character varying, '已批准'::character varying, '已拒绝'::character varying, '已处理'::character varying])::text[]))),
    CONSTRAINT chk_lr_type CHECK (((leave_type)::text = ANY ((ARRAY['事假'::character varying, '病假'::character varying, '公假'::character varying, '其他'::character varying])::text[])))
);


ALTER TABLE public.leave_request OWNER TO postgres;

--
-- Name: TABLE leave_request; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.leave_request IS '医生请假申请表';


--
-- Name: leave_request_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.leave_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.leave_request_id_seq OWNER TO postgres;

--
-- Name: leave_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.leave_request_id_seq OWNED BY public.leave_request.id;


--
-- Name: medical_record; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.medical_record OWNER TO postgres;

--
-- Name: TABLE medical_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.medical_record IS '患者病历表';


--
-- Name: medical_record_disease; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medical_record_disease (
    medical_record_id integer NOT NULL,
    disease_id integer NOT NULL
);


ALTER TABLE public.medical_record_disease OWNER TO postgres;

--
-- Name: TABLE medical_record_disease; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.medical_record_disease IS '病历-疾病关联表';


--
-- Name: medical_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_record_id_seq OWNER TO postgres;

--
-- Name: medical_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.medical_record_id_seq OWNED BY public.medical_record.id;


--
-- Name: medical_technology; Type: TABLE; Schema: public; Owner: postgres
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
    name character varying(255) DEFAULT NULL::character varying,
    code character varying(64) DEFAULT NULL::character varying,
    type character varying(64) DEFAULT NULL::character varying,
    department_id integer,
    department_name character varying(64) DEFAULT NULL::character varying,
    price numeric(8,2) DEFAULT 0.00 NOT NULL,
    specimen_type character varying(64) DEFAULT NULL::character varying,
    container character varying(64) DEFAULT NULL::character varying,
    instructions text,
    preparation text,
    turnaround_time integer,
    status smallint DEFAULT 1 NOT NULL,
    description text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    ai_category_code character varying(64),
    CONSTRAINT chk_medtech_price CHECK ((tech_price >= (0)::numeric)),
    CONSTRAINT chk_medtech_price_new CHECK ((price >= (0)::numeric)),
    CONSTRAINT chk_medtech_status CHECK ((status = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_medtech_type CHECK (((tech_type)::text = ANY ((ARRAY['check'::character varying, 'inspection'::character varying, 'disposal'::character varying])::text[])))
);


ALTER TABLE public.medical_technology OWNER TO postgres;

--
-- Name: TABLE medical_technology; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.medical_technology IS '医技项目表（检查/检验/处置统一目录）';


--
-- Name: COLUMN medical_technology.tech_type; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.medical_technology.tech_type IS '项目类型: check-检查, inspection-检验, disposal-处置';


--
-- Name: medical_technology_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.medical_technology_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.medical_technology_id_seq OWNER TO postgres;

--
-- Name: medical_technology_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.medical_technology_id_seq OWNED BY public.medical_technology.id;


--
-- Name: patient; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.patient (
    id integer NOT NULL,
    real_name character varying(64) DEFAULT ''::character varying NOT NULL,
    id_card character varying(18) DEFAULT NULL::character varying,
    gender character varying(6) DEFAULT NULL::character varying,
    birthdate date,
    phone character varying(20) DEFAULT NULL::character varying,
    avatar character varying(255) DEFAULT NULL::character varying,
    home_address character varying(255) DEFAULT NULL::character varying,
    allergy_history character varying(512) DEFAULT NULL::character varying,
    delmark smallint DEFAULT 1 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    account_balance numeric(10,2) DEFAULT 0.00 NOT NULL,
    CONSTRAINT chk_patient_account_balance CHECK ((account_balance >= (0)::numeric)),
    CONSTRAINT chk_patient_delmark CHECK ((delmark = ANY (ARRAY[0, 1]))),
    CONSTRAINT chk_patient_gender CHECK (((gender IS NULL) OR ((gender)::text = ANY ((ARRAY['男'::character varying, '女'::character varying])::text[]))))
);


ALTER TABLE public.patient OWNER TO postgres;

--
-- Name: TABLE patient; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.patient IS '患者档案表';


--
-- Name: COLUMN patient.id_card; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.patient.id_card IS '身份证号，唯一';


--
-- Name: COLUMN patient.allergy_history; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.patient.allergy_history IS '过敏史';


--
-- Name: COLUMN patient.delmark; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.patient.delmark IS '软删除标记: 0-有效, 1-已删除';


--
-- Name: COLUMN patient.account_balance; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.patient.account_balance IS '患者账户余额';


--
-- Name: patient_balance_transaction; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.patient_balance_transaction (
    id bigint NOT NULL,
    transaction_no character varying(64) NOT NULL,
    patient_id integer NOT NULL,
    transaction_type character varying(16) NOT NULL,
    amount numeric(10,2) NOT NULL,
    balance_before numeric(10,2) NOT NULL,
    balance_after numeric(10,2) NOT NULL,
    business_type character varying(64) DEFAULT NULL::character varying,
    business_id bigint,
    operator_id bigint,
    operator_name character varying(64) DEFAULT NULL::character varying,
    remark character varying(255) DEFAULT NULL::character varying,
    transaction_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_patient_balance_transaction_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT chk_patient_balance_transaction_balance CHECK (((balance_before >= (0)::numeric) AND (balance_after >= (0)::numeric))),
    CONSTRAINT chk_patient_balance_transaction_type CHECK (((transaction_type)::text = ANY ((ARRAY['RECHARGE'::character varying, 'DEDUCT'::character varying, 'REFUND'::character varying])::text[])))
);


ALTER TABLE public.patient_balance_transaction OWNER TO postgres;

--
-- Name: TABLE patient_balance_transaction; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.patient_balance_transaction IS '患者余额流水表';


--
-- Name: COLUMN patient_balance_transaction.transaction_type; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.patient_balance_transaction.transaction_type IS '交易类型: RECHARGE-充值, DEDUCT-扣款, REFUND-退款';


--
-- Name: patient_balance_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.patient_balance_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.patient_balance_transaction_id_seq OWNER TO postgres;

--
-- Name: patient_balance_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.patient_balance_transaction_id_seq OWNED BY public.patient_balance_transaction.id;


--
-- Name: patient_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.patient_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.patient_id_seq OWNER TO postgres;

--
-- Name: patient_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.patient_id_seq OWNED BY public.patient.id;


--
-- Name: prescription; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.prescription OWNER TO postgres;

--
-- Name: TABLE prescription; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.prescription IS '处方表';


--
-- Name: COLUMN prescription.drug_state; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.prescription.drug_state IS '状态: 未发 → 已发，或 未发 → 已退';


--
-- Name: prescription_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.prescription_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.prescription_id_seq OWNER TO postgres;

--
-- Name: prescription_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.prescription_id_seq OWNED BY public.prescription.id;


--
-- Name: regist_level; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.regist_level OWNER TO postgres;

--
-- Name: TABLE regist_level; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.regist_level IS '挂号级别表';


--
-- Name: COLUMN regist_level.regist_code; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.regist_code IS '号别编码';


--
-- Name: COLUMN regist_level.regist_name; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.regist_name IS '号别名称';


--
-- Name: COLUMN regist_level.regist_fee; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.regist_fee IS '挂号费（元）';


--
-- Name: COLUMN regist_level.regist_quota; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.regist_quota IS '每半天挂号限额';


--
-- Name: COLUMN regist_level.sequence_no; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.sequence_no IS '显示顺序号';


--
-- Name: COLUMN regist_level.delmark; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.regist_level.delmark IS '软删除标记: 0-有效, 1-已删除';


--
-- Name: regist_level_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.regist_level_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.regist_level_id_seq OWNER TO postgres;

--
-- Name: regist_level_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.regist_level_id_seq OWNED BY public.regist_level.id;


--
-- Name: register; Type: TABLE; Schema: public; Owner: postgres
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
    patient_id bigint,
    scheduling_id bigint,
    check_in_time timestamp without time zone,
    CONSTRAINT chk_register_gender CHECK (((gender IS NULL) OR ((gender)::text = ANY ((ARRAY['男'::character varying, '女'::character varying])::text[])))),
    CONSTRAINT chk_register_is_book CHECK (((is_book)::text = ANY ((ARRAY['是'::character varying, '否'::character varying])::text[]))),
    CONSTRAINT chk_register_noon CHECK (((noon IS NULL) OR ((noon)::text = ANY ((ARRAY['上午'::character varying, '下午'::character varying, '晚上'::character varying])::text[])))),
    CONSTRAINT chk_register_regist_money CHECK ((regist_money >= (0)::numeric)),
    CONSTRAINT chk_register_visit_state CHECK ((visit_state = ANY (ARRAY[1, 2, 3, 4, 5, 6])))
);


ALTER TABLE public.register OWNER TO postgres;

--
-- Name: TABLE register; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.register IS '患者历次挂号信息表';


--
-- Name: COLUMN register.case_number; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.register.case_number IS '病历号';


--
-- Name: COLUMN register.visit_state; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.register.visit_state IS '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号, 5-检查检验中, 6-检查检验完成';


--
-- Name: COLUMN register.patient_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.register.patient_id IS '患者ID';


--
-- Name: COLUMN register.scheduling_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.register.scheduling_id IS '排班ID';


--
-- Name: COLUMN register.check_in_time; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.register.check_in_time IS '报到时间';


--
-- Name: register_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.register_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.register_id_seq OWNER TO postgres;

--
-- Name: register_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.register_id_seq OWNED BY public.register.id;


--
-- Name: result_form_category; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.result_form_category (
    category_code character varying(64) NOT NULL,
    category_name character varying(128) NOT NULL,
    description character varying(512) DEFAULT NULL::character varying
);


ALTER TABLE public.result_form_category OWNER TO postgres;

--
-- Name: result_form_field; Type: TABLE; Schema: public; Owner: postgres
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


ALTER TABLE public.result_form_field OWNER TO postgres;

--
-- Name: result_form_field_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.result_form_field_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.result_form_field_id_seq OWNER TO postgres;

--
-- Name: result_form_field_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.result_form_field_id_seq OWNED BY public.result_form_field.id;


--
-- Name: schedule_adjust_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.schedule_adjust_log (
    id integer NOT NULL,
    schedule_id integer NOT NULL,
    field_name character varying(32) NOT NULL,
    old_value character varying(255) DEFAULT NULL::character varying,
    new_value character varying(255) DEFAULT NULL::character varying,
    adjust_type character varying(16) NOT NULL,
    adjust_by integer NOT NULL,
    adjust_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    remark character varying(255) DEFAULT NULL::character varying,
    delmark smallint DEFAULT 0 NOT NULL
);


ALTER TABLE public.schedule_adjust_log OWNER TO postgres;

--
-- Name: TABLE schedule_adjust_log; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.schedule_adjust_log IS '排班调整日志表';


--
-- Name: schedule_adjust_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.schedule_adjust_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.schedule_adjust_log_id_seq OWNER TO postgres;

--
-- Name: schedule_adjust_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.schedule_adjust_log_id_seq OWNED BY public.schedule_adjust_log.id;


--
-- Name: schedule_adjust_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.schedule_adjust_request (
    id integer NOT NULL,
    schedule_id integer NOT NULL,
    adjust_type character varying(16) NOT NULL,
    old_physician_id integer,
    new_physician_id integer,
    old_status character varying(16) DEFAULT NULL::character varying,
    new_status character varying(16) DEFAULT NULL::character varying,
    old_quota integer,
    new_quota integer,
    reason text,
    ai_suggestion text,
    affect_patients integer DEFAULT 0,
    triggered_by integer NOT NULL,
    status character varying(16) DEFAULT '待确认'::character varying NOT NULL,
    confirmed_by integer,
    confirm_time timestamp without time zone,
    confirm_remark character varying(255) DEFAULT NULL::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    delmark smallint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_sar_status CHECK (((status)::text = ANY ((ARRAY['待确认'::character varying, '已确认'::character varying, '已驳回'::character varying])::text[]))),
    CONSTRAINT chk_sar_type CHECK (((adjust_type)::text = ANY ((ARRAY['leave_ai'::character varying, 'admin_urgent'::character varying, 'system'::character varying])::text[])))
);


ALTER TABLE public.schedule_adjust_request OWNER TO postgres;

--
-- Name: TABLE schedule_adjust_request; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.schedule_adjust_request IS '排班调整申请表';


--
-- Name: schedule_adjust_request_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.schedule_adjust_request_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.schedule_adjust_request_id_seq OWNER TO postgres;

--
-- Name: schedule_adjust_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.schedule_adjust_request_id_seq OWNED BY public.schedule_adjust_request.id;


--
-- Name: schedule_plan; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.schedule_plan (
    id integer NOT NULL,
    plan_name character varying(64) NOT NULL,
    department_id integer NOT NULL,
    plan_month character varying(7) NOT NULL,
    status character varying(16) DEFAULT '草稿'::character varying NOT NULL,
    ai_generated boolean DEFAULT false NOT NULL,
    ai_version integer,
    total_schedules integer DEFAULT 0,
    total_quota integer DEFAULT 0,
    created_by integer,
    created_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    published_time timestamp without time zone,
    published_by integer,
    delmark smallint DEFAULT 0 NOT NULL,
    CONSTRAINT chk_sp_status CHECK (((status)::text = ANY ((ARRAY['草稿'::character varying, '待审核'::character varying, '已发布'::character varying])::text[])))
);


ALTER TABLE public.schedule_plan OWNER TO postgres;

--
-- Name: TABLE schedule_plan; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.schedule_plan IS '排班计划表';


--
-- Name: schedule_plan_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.schedule_plan_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.schedule_plan_id_seq OWNER TO postgres;

--
-- Name: schedule_plan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.schedule_plan_id_seq OWNED BY public.schedule_plan.id;


--
-- Name: settle_category; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.settle_category (
    id integer NOT NULL,
    settle_code character varying(64) NOT NULL,
    settle_name character varying(64) NOT NULL,
    sequence_no integer DEFAULT 0,
    delmark smallint DEFAULT 1 NOT NULL,
    CONSTRAINT chk_settle_category_delmark CHECK ((delmark = ANY (ARRAY[0, 1])))
);


ALTER TABLE public.settle_category OWNER TO postgres;

--
-- Name: TABLE settle_category; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.settle_category IS '结算类别表';


--
-- Name: settle_category_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.settle_category_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.settle_category_id_seq OWNER TO postgres;

--
-- Name: settle_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.settle_category_id_seq OWNED BY public.settle_category.id;


--
-- Name: triage_desk_record; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.triage_desk_record (
    id bigint NOT NULL,
    patient_id integer,
    patient_name character varying(64) DEFAULT NULL::character varying,
    patient_phone character varying(20) DEFAULT NULL::character varying,
    symptoms text,
    ai_triage_result text,
    recommended_department_id integer,
    recommended_department character varying(64) DEFAULT NULL::character varying,
    recommended_physician_id integer,
    recommended_physician_name character varying(64) DEFAULT NULL::character varying,
    risk_level character varying(16) DEFAULT NULL::character varying,
    ai_analysis text,
    status smallint DEFAULT 0 NOT NULL,
    confirmed_department_id integer,
    confirmed_department character varying(64) DEFAULT NULL::character varying,
    confirmed_physician_id integer,
    confirmed_physician_name character varying(64) DEFAULT NULL::character varying,
    operator_id integer,
    operator_name character varying(64) DEFAULT NULL::character varying,
    confirm_remark character varying(255) DEFAULT NULL::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    confirm_time timestamp without time zone
);


ALTER TABLE public.triage_desk_record OWNER TO postgres;

--
-- Name: TABLE triage_desk_record; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.triage_desk_record IS '导诊台记录表';


--
-- Name: triage_desk_record_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.triage_desk_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.triage_desk_record_id_seq OWNER TO postgres;

--
-- Name: triage_desk_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.triage_desk_record_id_seq OWNED BY public.triage_desk_record.id;


--
-- Name: user_patient_managed; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_patient_managed (
    user_id integer NOT NULL,
    patient_id integer NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    relation character varying(16) DEFAULT NULL::character varying
);


ALTER TABLE public.user_patient_managed OWNER TO postgres;

--
-- Name: TABLE user_patient_managed; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.user_patient_managed IS '用户管理的患者列表（本人+家人）';


--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    username character varying(64) NOT NULL,
    password character varying(255) NOT NULL,
    real_name character varying(64) DEFAULT NULL::character varying,
    email character varying(128) DEFAULT NULL::character varying,
    phone character varying(20) DEFAULT NULL::character varying,
    id_card character varying(18) DEFAULT NULL::character varying,
    gender character varying(6) DEFAULT NULL::character varying,
    birthdate date,
    avatar character varying(255) DEFAULT NULL::character varying,
    patient_id integer,
    status integer DEFAULT 1 NOT NULL,
    user_type integer DEFAULT 6 NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_gender CHECK (((gender IS NULL) OR ((gender)::text = ANY ((ARRAY['男'::character varying, '女'::character varying])::text[])))),
    CONSTRAINT chk_users_status CHECK ((status = ANY (ARRAY[1, 0, '-1'::integer])))
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: TABLE users; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON TABLE public.users IS '系统用户表（登录认证）';


--
-- Name: COLUMN users.id_card; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.id_card IS '身份证号，用于实名认证和医保关联';


--
-- Name: COLUMN users.gender; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.gender IS '性别: 男/女';


--
-- Name: COLUMN users.birthdate; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.birthdate IS '出生日期';


--
-- Name: COLUMN users.avatar; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.avatar IS '头像URL';


--
-- Name: COLUMN users.patient_id; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.patient_id IS '关联的患者档案ID';


--
-- Name: COLUMN users.user_type; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.users.user_type IS '用户类型: 1-管理员, 2-医生, 3-挂号员, 4-医技人员, 5-药房人员, 6-患者';


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO postgres;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: ai_consultation_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_consultation_record ALTER COLUMN id SET DEFAULT nextval('public.ai_consultation_record_id_seq'::regclass);


--
-- Name: ai_diagnosis_suggestion id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion ALTER COLUMN id SET DEFAULT nextval('public.ai_diagnosis_suggestion_id_seq'::regclass);


--
-- Name: ai_exam_analysis id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_analysis ALTER COLUMN id SET DEFAULT nextval('public.ai_exam_analysis_id_seq'::regclass);


--
-- Name: ai_exam_suggestion id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_suggestion ALTER COLUMN id SET DEFAULT nextval('public.ai_exam_suggestion_id_seq'::regclass);


--
-- Name: ai_follow_up_plan id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_plan ALTER COLUMN id SET DEFAULT nextval('public.ai_follow_up_plan_id_seq'::regclass);


--
-- Name: ai_follow_up_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_record ALTER COLUMN id SET DEFAULT nextval('public.ai_follow_up_record_id_seq'::regclass);


--
-- Name: ai_medical_record_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_medical_record_log ALTER COLUMN id SET DEFAULT nextval('public.ai_medical_record_log_id_seq'::regclass);


--
-- Name: ai_prescription_review id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_prescription_review ALTER COLUMN id SET DEFAULT nextval('public.ai_prescription_review_id_seq'::regclass);


--
-- Name: ai_triage_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_triage_record ALTER COLUMN id SET DEFAULT nextval('public.ai_triage_record_id_seq'::regclass);


--
-- Name: check_request id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request ALTER COLUMN id SET DEFAULT nextval('public.check_request_id_seq'::regclass);


--
-- Name: department id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department ALTER COLUMN id SET DEFAULT nextval('public.department_id_seq'::regclass);


--
-- Name: disease id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disease ALTER COLUMN id SET DEFAULT nextval('public.disease_id_seq'::regclass);


--
-- Name: disposal_request id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request ALTER COLUMN id SET DEFAULT nextval('public.disposal_request_id_seq'::regclass);


--
-- Name: doctor_schedule id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule ALTER COLUMN id SET DEFAULT nextval('public.doctor_schedule_id_seq'::regclass);


--
-- Name: drug_info id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.drug_info ALTER COLUMN id SET DEFAULT nextval('public.drug_info_id_seq'::regclass);


--
-- Name: employee id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee ALTER COLUMN id SET DEFAULT nextval('public.employee_id_seq'::regclass);


--
-- Name: expense_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expense_record ALTER COLUMN id SET DEFAULT nextval('public.expense_record_id_seq'::regclass);


--
-- Name: inspection_request id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request ALTER COLUMN id SET DEFAULT nextval('public.inspection_request_id_seq'::regclass);


--
-- Name: leave_request id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request ALTER COLUMN id SET DEFAULT nextval('public.leave_request_id_seq'::regclass);


--
-- Name: medical_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record ALTER COLUMN id SET DEFAULT nextval('public.medical_record_id_seq'::regclass);


--
-- Name: medical_technology id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_technology ALTER COLUMN id SET DEFAULT nextval('public.medical_technology_id_seq'::regclass);


--
-- Name: patient id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient ALTER COLUMN id SET DEFAULT nextval('public.patient_id_seq'::regclass);


--
-- Name: patient_balance_transaction id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient_balance_transaction ALTER COLUMN id SET DEFAULT nextval('public.patient_balance_transaction_id_seq'::regclass);


--
-- Name: prescription id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prescription ALTER COLUMN id SET DEFAULT nextval('public.prescription_id_seq'::regclass);


--
-- Name: regist_level id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regist_level ALTER COLUMN id SET DEFAULT nextval('public.regist_level_id_seq'::regclass);


--
-- Name: register id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register ALTER COLUMN id SET DEFAULT nextval('public.register_id_seq'::regclass);


--
-- Name: result_form_field id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.result_form_field ALTER COLUMN id SET DEFAULT nextval('public.result_form_field_id_seq'::regclass);


--
-- Name: schedule_adjust_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_log ALTER COLUMN id SET DEFAULT nextval('public.schedule_adjust_log_id_seq'::regclass);


--
-- Name: schedule_adjust_request id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_request ALTER COLUMN id SET DEFAULT nextval('public.schedule_adjust_request_id_seq'::regclass);


--
-- Name: schedule_plan id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_plan ALTER COLUMN id SET DEFAULT nextval('public.schedule_plan_id_seq'::regclass);


--
-- Name: settle_category id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settle_category ALTER COLUMN id SET DEFAULT nextval('public.settle_category_id_seq'::regclass);


--
-- Name: triage_desk_record id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.triage_desk_record ALTER COLUMN id SET DEFAULT nextval('public.triage_desk_record_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: ai_consultation_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_consultation_record (id, register_id, round_number, ai_question, patient_answer, consultation_state, chief_complaint, symptom_duration, history_summary, allergy_summary, medication_summary, ai_summary, suggested_exam, creation_time, completion_time, model_id, patient_id, session_uuid, updated_at) FROM stdin;
\.


--
-- Data for Name: ai_diagnosis_suggestion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_diagnosis_suggestion (id, register_id, disease_id, disease_name, recommend_icd, probability, risk_level, treatment_direction, diagnosis_basis, is_adopted, sort_order, creation_time, model_id) FROM stdin;
\.


--
-- Data for Name: ai_exam_analysis; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_exam_analysis (id, register_id, check_request_id, inspection_request_id, analysis_type, original_result, abnormal_indicators, risk_level, analysis_report, correlation_analysis, is_viewed, analysis_time, model_id) FROM stdin;
\.


--
-- Data for Name: ai_exam_suggestion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_exam_suggestion (id, register_id, tech_id, tech_name, suggest_type, suggest_reason, priority, is_adopted, creation_time, model_id) FROM stdin;
3	2	4	血常规	inspection	评估感染、炎症及血液系统情况，为基本筛查项目。	1	0	2026-06-18 16:04:15.41419	dify-w2
4	2	6	肝功能	inspection	患者面部发黄发黑，需排除肝功能损伤，与现病史相关。	1	0	2026-06-18 16:04:15.41419	dify-w2
5	2	3	腹部超声	check	评估肝胆系统结构，排除梗阻或占位性病变，与面部发黄相关。	2	0	2026-06-18 16:04:15.41419	dify-w2
\.


--
-- Data for Name: ai_follow_up_plan; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_follow_up_plan (id, register_id, prescription_id, follow_up_day, planned_date, follow_up_type, content_template, plan_status, creation_time, model_id) FROM stdin;
\.


--
-- Data for Name: ai_follow_up_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_follow_up_record (id, follow_up_plan_id, register_id, is_on_time, side_effect, has_side_effect, symptom_relief, need_revisit, patient_feedback, ai_assessment, ai_advice, follow_up_time, model_id) FROM stdin;
\.


--
-- Data for Name: ai_medical_record_log; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_medical_record_log (id, register_id, medical_record_id, source_type, ai_readme, ai_present, ai_history, ai_allergy, ai_physique, ai_diagnosis, is_adopted, doctor_modification, generation_time, model_id) FROM stdin;
1	2	\N	preliminary_diagnosis	\N	\N	\N	\N	\N	根据您描述的症状：昨日突然畏寒寒颤、全身肌肉酸痛、乏力、低热37.9°C，服用抗病毒口服液后明显好转，但仍有头痛、乏力、面色发黄发黑。知识库召回内容提示流行性感冒（流感）常见症状包括突然发热、畏寒、肌肉酸痛、乏力等，与起始症状高度吻合。但面色发黄发黑非典型流感表现，需警惕其他可能。初步考虑：1. 流行性感冒（恢复期），但需排除其他病因如肝功能异常或药物反应。2. 病毒性肝炎早期或感染后肝功能损伤，因面部发黄发黑可能提示黄疸或色素沉着。建议就诊完善血常规、肝功能等检查。回答仅供参考。	0	{"diagnosisBasis":"流行性感冒 — 起病急，畏寒、肌肉酸痛、乏力、低热，抗病毒治疗后症状快速缓解，符合流感特征\\n肝功能异常/病毒性肝炎 — 面部发黄发黑提示可能黄疸或肝损伤，病毒性感染后可能出现","knowledgeBaseRecall":"流行性感冒常见症状：突然发热、畏寒、咳嗽、咽痛、头痛、肌肉酸痛、明显乏力，可伴流涕。疾病名称：流行性感冒。","isRecalled":"true","confidence":70.0,"clinicalSummary":"起始症状符合流行性感冒，恢复期出现面部发黄发黑需警惕肝功能异常","primaryDiagnosis":"流行性感冒","suggestedDiseases":[{"diseaseName":"流行性感冒","confidenceLevel":"80","rank":1,"rationale":"起病急，畏寒、肌肉酸痛、乏力、低热，抗病毒治疗后症状快速缓解，符合流感特征","keyEvidence":["畏寒寒颤","全身肌肉酸痛","乏力","低热"],"missingOrWeakEvidence":["无咳嗽咽痛","面部发黄发黑非典型症状"],"recommendedWorkup":["血常规","流感病毒抗原检测"]},{"diseaseName":"肝功能异常/病毒性肝炎","confidenceLevel":"60","rank":2,"rationale":"面部发黄发黑提示可能黄疸或肝损伤，病毒性感染后可能出现","keyEvidence":["面部发黄发黑","乏力","近期感染史"],"missingOrWeakEvidence":["无恶心呕吐","无肝区疼痛","无既往肝病史"],"recommendedWorkup":["肝功能","腹部超声","肝炎病毒标志物"]}],"excludedDiagnoses":null,"redFlags":null,"preHandle":false,"workflowRunId":"a9e884b7-6728-4b39-abce-5f8e2d1d5b82","llmModel":"deepseek-v4-flash"}	2026-06-18 15:56:33.04645	dify-preliminary
2	2	\N	preliminary_diagnosis	\N	\N	\N	\N	\N	根据您描述的症状（突然畏寒寒颤、全身肌肉酸痛、乏力、低烧），结合知识库中流行性感冒的常见症状（突然发热、畏寒、肌肉酸痛、乏力等），您的朋友很可能患有流行性感冒。服用抗病毒口服液后症状明显好转，也支持病毒性感染的可能。但面部发黄发黑、运动时头痛、仍乏力，需注意是否存在其他问题如肝功能异常、脱水或恢复期表现。建议观察是否出现黄疸、恶心等症状，必要时就医检查。本回答仅供参考，不能替代专业诊断。	0	{"diagnosisBasis":"流行性感冒 — 症状符合流感典型表现：突畏寒、肌肉酸痛、乏力、低热，抗病毒治疗后迅速好转。","knowledgeBaseRecall":"流行性感冒常见症状：突然发热、畏寒、咳嗽、咽痛、头痛、肌肉酸痛、明显乏力，可伴流涕。","isRecalled":"true","confidence":80.0,"clinicalSummary":"突发畏寒、肌肉酸痛、低热，抗病毒治疗后好转，但仍有头痛、乏力、面色异常，考虑流行性感冒恢复期，需排除其他感染或肝功问题。","primaryDiagnosis":"流行性感冒","suggestedDiseases":[{"diseaseName":"流行性感冒","confidenceLevel":"80","rank":1,"rationale":"症状符合流感典型表现：突畏寒、肌肉酸痛、乏力、低热，抗病毒治疗后迅速好转。","keyEvidence":["畏寒寒颤","全身肌肉酸痛","乏力","低烧37.9°C","抗病毒口服液有效"],"missingOrWeakEvidence":["未描述咳嗽、咽痛等呼吸道症状","面部发黄发黑原因不明"],"recommendedWorkup":["血常规","流感病毒抗原检测","肝功能检查"]}],"excludedDiagnoses":[],"redFlags":["面部发黄发黑需排除黄疸、肝功能异常","运动性头痛持续需警惕颅内问题"],"preHandle":false,"workflowRunId":"c3f87394-8195-48f4-b43c-b7675fb0a70d","llmModel":"deepseek-v4-flash"}	2026-06-18 15:57:23.800076	dify-preliminary
3	2	\N	preliminary_diagnosis	\N	\N	\N	\N	\N	流行性感冒	0	{"suggestedDiseaseNames":["流行性感冒"],"savedBy":"doctor"}	2026-06-18 15:57:50.267371	doctor-save
4	2	\N	preliminary_diagnosis	\N	\N	\N	\N	\N	根据您的描述：昨日突发畏寒寒战、全身肌肉酸痛、乏力、低烧37.9℃，服用抗病毒口服液后好转，现仍有头痛、乏力、面色发黄发黑。知识库中流行性感冒的典型症状（突然发热、畏寒、肌肉酸痛、明显乏力）与您的情况高度吻合，且抗病毒治疗有效，因此优先考虑流行性感冒恢复期。但面部发黄发黑需警惕药物反应或其他原因（如肝脏问题），建议观察并完善相关检查。	0	{"diagnosisBasis":"流行性感冒 — 患者急性起病，畏寒寒战、全身肌肉酸痛、乏力、低热，符合流行性感冒典型症状，且抗病毒口服液有效，病程符合自限性特点。","knowledgeBaseRecall":"流行性感冒常见症状：突然发热、畏寒、咳嗽、咽痛、头痛、肌肉酸痛、明显乏力，可伴流涕。","isRecalled":"true","confidence":80.0,"clinicalSummary":"患者急性起病，表现为畏寒寒战、全身肌肉酸痛、乏力、低热，抗病毒治疗后症状明显好转，符合流行性感冒的临床过程；但面色发黄发黑需注意排除药物性肝损伤或其他肝胆疾病。","primaryDiagnosis":"流行性感冒","suggestedDiseases":[{"diseaseName":"流行性感冒","confidenceLevel":"80","rank":1,"rationale":"患者急性起病，畏寒寒战、全身肌肉酸痛、乏力、低热，符合流行性感冒典型症状，且抗病毒口服液有效，病程符合自限性特点。","keyEvidence":["畏寒寒战","全身肌肉酸痛","乏力","低热37.9℃","抗病毒治疗后好转"],"missingOrWeakEvidence":["未描述咳嗽、咽痛、流涕等症状","面部发黄发黑原因待查"],"recommendedWorkup":["血常规","流感病毒抗原检测","肝功能检查"]}],"excludedDiagnoses":[],"redFlags":["面色发黄发黑需排除肝脏疾病或药物反应"],"preHandle":true,"workflowRunId":"91de9b84-05e4-4e55-bb81-a027e287c007","llmModel":"deepseek-v4-flash"}	2026-06-18 15:59:03.756695	dify-preliminary
5	2	\N	preliminary_diagnosis	\N	\N	\N	\N	\N	流行性感冒	0	{"suggestedDiseaseNames":["流行性感冒"],"savedBy":"doctor"}	2026-06-18 15:59:31.919545	doctor-save
\.


--
-- Data for Name: ai_prescription_review; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_prescription_review (id, register_id, prescription_id, review_result, drug_conflict, allergy_risk, duplicate_drug, dosage_check, risk_score, risk_details, doctor_action, review_time, model_id) FROM stdin;
\.


--
-- Data for Name: ai_triage_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ai_triage_record (id, patient_name, patient_age, patient_gender, symptom_description, recommend_dept_id, recommend_dept_name, recommend_doctor_id, recommend_doctor_name, risk_level, is_priority, ai_analysis, register_id, triage_time, model_id) FROM stdin;
\.


--
-- Data for Name: check_request; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.check_request (id, register_id, medical_technology_id, check_info, check_position, creation_time, check_employee_id, inputcheck_employee_id, check_time, check_result, check_state, check_remark) FROM stdin;
\.


--
-- Data for Name: department; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.department (id, dept_code, dept_name, dept_type, dept_description, delmark) FROM stdin;
1	NK	内科	临床科室	常见内科疾病、慢病复诊、发热乏力等综合性问题的首选科室。	0
2	HXNK	呼吸内科	临床科室	关注咳嗽、气喘、肺炎、慢阻肺、支气管炎等呼吸系统疾病。	0
3	XXNK	心血管内科	临床科室	处理胸闷胸痛、心悸、高血压、冠心病、心律失常等问题。	0
4	XHNK	消化内科	临床科室	面向胃痛腹胀、反酸、腹泻、肝胆胰及消化道相关疾病。	0
5	SJNK	神经内科	临床科室	关注头痛头晕、失眠、肢体麻木、脑血管和神经系统疾病。	0
6	SNK	肾内科	临床科室	处理水肿、尿检异常、肾炎、肾功能异常及慢性肾病管理。	0
7	NFMK	内分泌科	临床科室	面向糖尿病、甲状腺疾病、代谢异常、肥胖和骨质疏松等问题。	0
8	WK	外科	临床科室	处理体表包块、外伤、腹部外科疾病及需要手术评估的问题。	0
9	GC	骨科	临床科室	关注关节疼痛、骨折损伤、颈肩腰腿痛、运动损伤等骨骼肌肉问题。	0
10	FCHK	妇产科	临床科室	提供妇科疾病、孕产期咨询、月经异常和女性健康管理服务。	0
11	EK	儿科	临床科室	面向儿童发热、咳嗽、腹泻、过敏、生长发育等常见问题。	0
12	XSEK	新生儿科	临床科室	关注新生儿喂养、黄疸、早产儿随访和出生后健康评估。	0
13	YFK	眼科	临床科室	处理视力下降、眼红眼痛、干眼、白内障、青光眼等眼部问题。	0
14	EBHK	耳鼻咽喉科	临床科室	面向鼻炎、咽喉不适、耳鸣听力下降、扁桃体和鼻窦问题。	0
15	KQK	口腔科	临床科室	提供牙痛、龋齿、牙周问题、口腔黏膜和口腔保健服务。	0
16	PFK	皮肤科	临床科室	处理皮疹、瘙痒、痤疮、湿疹、过敏和感染性皮肤问题。	0
17	ZYK	中医科	临床科室	结合中医辨证，提供慢病调理、体质调养和康复辅助服务。	0
18	ZLK	肿瘤科	临床科室	面向肿瘤筛查咨询、治疗评估、复查随访和症状管理。	0
19	JZK	急诊科	临床科室	处理突发不适、急性疼痛、外伤和需要快速评估的急症情况。	0
20	KFY	康复医学科	临床科室	提供术后、卒中、骨伤和慢病后的功能恢复与康复指导。	0
35	FSK	放射科	医技科室	\N	0
36	CSK	超声科	医技科室	\N	0
37	JYK	检验科	医技科室	\N	0
38	YXK	输血科	医技科室	\N	0
39	BLK	病理科	医技科室	\N	0
40	CZK	处置室	医技科室	\N	0
41	NJZX	内镜中心	医技科室	\N	0
42	SS	手术室	医技科室	\N	0
44	XDZX	消毒供应中心	医技科室	\N	0
45	YF	药房	医技科室	\N	0
\.


--
-- Data for Name: disease; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.disease (id, disease_code, disease_name, diseaseicd, disease_category) FROM stdin;
\.


--
-- Data for Name: disposal_request; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.disposal_request (id, register_id, medical_technology_id, disposal_info, disposal_position, creation_time, disposal_employee_id, inputdisposal_employee_id, disposal_time, disposal_result, disposal_state, disposal_remark) FROM stdin;
\.


--
-- Data for Name: doctor_schedule; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.doctor_schedule (id, plan_id, physician_id, department_id, work_date, time_slot, regist_level_id, total_quota, used_quota, available_quota, price, status, ai_suggestion, modified, modify_remark, created_time, update_time, delmark) FROM stdin;
1	1	1	1	2026-06-01	上午	1	50	0	50	15.00	正常	周一需求高，号源充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
2	1	2	1	2026-06-01	上午	1	50	0	50	15.00	正常	需求旺盛，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
3	1	4	1	2026-06-01	上午	2	25	0	25	20.00	正常	专家号需求稳定	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
4	1	3	1	2026-06-01	下午	1	50	0	50	15.00	正常	需求充足，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
5	1	5	1	2026-06-01	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
6	1	2	1	2026-06-02	上午	1	50	0	50	15.00	正常	周二需求较高	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
7	1	3	1	2026-06-02	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
8	1	5	1	2026-06-02	上午	3	30	0	30	30.00	正常	主任号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
9	1	1	1	2026-06-02	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
10	1	4	1	2026-06-02	下午	2	25	0	25	20.00	正常	专家号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
11	1	1	1	2026-06-03	上午	1	50	0	50	15.00	正常	周三需求稍低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
12	1	2	1	2026-06-03	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
13	1	3	1	2026-06-03	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
14	1	4	1	2026-06-03	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
15	1	2	1	2026-06-04	上午	1	50	0	50	15.00	正常	周四需求较低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
16	1	3	1	2026-06-04	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
17	1	1	1	2026-06-04	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
18	1	5	1	2026-06-04	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
19	1	1	1	2026-06-05	上午	1	50	0	50	15.00	正常	周五需求中等	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
20	1	3	1	2026-06-05	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
21	1	4	1	2026-06-05	上午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
22	1	2	1	2026-06-05	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
23	1	5	1	2026-06-05	下午	3	30	0	30	30.00	正常	主任号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
24	1	1	1	2026-06-06	上午	1	50	0	50	15.00	正常	周六仅上午出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
25	1	2	1	2026-06-07	上午	1	50	0	50	15.00	正常	节假日精简出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
26	1	3	1	2026-06-07	下午	1	50	0	50	15.00	正常	节假日出诊人数少	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
27	1	2	1	2026-06-08	上午	1	50	0	50	15.00	正常	周一需求高，号源充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
28	1	3	1	2026-06-08	上午	1	50	0	50	15.00	正常	需求旺盛，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
29	1	5	1	2026-06-08	上午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
30	1	1	1	2026-06-08	下午	1	50	0	50	15.00	正常	需求充足，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
31	1	4	1	2026-06-08	下午	2	25	0	25	20.00	正常	专家号需求稳定	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
32	1	1	1	2026-06-09	上午	1	50	0	50	15.00	正常	周二需求较高	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
33	1	2	1	2026-06-09	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
34	1	4	1	2026-06-09	上午	2	25	0	25	20.00	正常	专家号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
35	1	3	1	2026-06-09	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
36	1	5	1	2026-06-09	下午	3	30	0	30	30.00	正常	主任号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
37	1	3	1	2026-06-10	上午	1	50	0	50	15.00	正常	周三需求稍低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
38	1	1	1	2026-06-10	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
39	1	2	1	2026-06-10	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
40	1	4	1	2026-06-10	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
41	1	1	1	2026-06-11	上午	1	50	0	50	15.00	正常	周四需求较低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
42	1	2	1	2026-06-11	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
43	1	3	1	2026-06-11	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
44	1	5	1	2026-06-11	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
45	1	2	1	2026-06-12	上午	1	50	0	50	15.00	正常	周五需求中等	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
46	1	3	1	2026-06-12	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
47	1	5	1	2026-06-12	上午	3	30	0	30	30.00	正常	主任号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
48	1	1	1	2026-06-12	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
49	1	4	1	2026-06-12	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
50	1	2	1	2026-06-13	上午	1	50	0	50	15.00	正常	周六仅上午出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
51	1	3	1	2026-06-14	上午	1	50	0	50	15.00	正常	节假日精简出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
52	1	1	1	2026-06-14	下午	1	50	0	50	15.00	正常	节假日出诊人数少	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
53	1	3	1	2026-06-15	上午	1	50	0	50	15.00	正常	周一需求高，号源充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
54	1	1	1	2026-06-15	上午	1	50	0	50	15.00	正常	需求旺盛，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
55	1	4	1	2026-06-15	上午	2	25	0	25	20.00	正常	专家号需求稳定	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
56	1	2	1	2026-06-15	下午	1	50	0	50	15.00	正常	需求充足，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
57	1	5	1	2026-06-15	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
58	1	2	1	2026-06-16	上午	1	50	0	50	15.00	正常	周二需求较高	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
59	1	1	1	2026-06-16	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
60	1	5	1	2026-06-16	上午	3	30	0	30	30.00	正常	主任号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
61	1	3	1	2026-06-16	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
62	1	4	1	2026-06-16	下午	2	25	0	25	20.00	正常	专家号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
63	1	1	1	2026-06-17	上午	1	50	0	50	15.00	正常	周三需求稍低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
64	1	3	1	2026-06-17	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
65	1	2	1	2026-06-17	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
66	1	5	1	2026-06-17	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
67	1	2	1	2026-06-18	上午	1	50	0	50	15.00	正常	周四需求较低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
68	1	3	1	2026-06-18	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
69	1	1	1	2026-06-18	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
70	1	4	1	2026-06-18	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
71	1	3	1	2026-06-19	上午	1	50	0	50	15.00	正常	周五需求中等	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
72	1	2	1	2026-06-19	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
73	1	4	1	2026-06-19	上午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
74	1	1	1	2026-06-19	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
75	1	5	1	2026-06-19	下午	3	30	0	30	30.00	正常	主任号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
76	1	3	1	2026-06-20	上午	1	50	0	50	15.00	正常	周六仅上午出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
77	1	1	1	2026-06-21	上午	1	50	0	50	15.00	正常	节假日精简出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
78	1	2	1	2026-06-21	下午	1	50	0	50	15.00	正常	节假日出诊人数少	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
79	1	2	1	2026-06-22	上午	1	50	0	50	15.00	正常	周一需求高，号源充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
80	1	3	1	2026-06-22	上午	1	50	0	50	15.00	正常	需求旺盛，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
81	1	5	1	2026-06-22	上午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
82	1	1	1	2026-06-22	下午	1	50	0	50	15.00	正常	需求充足，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
83	1	4	1	2026-06-22	下午	2	25	0	25	20.00	正常	专家号需求稳定	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
84	1	1	1	2026-06-23	上午	1	50	0	50	15.00	正常	周二需求较高	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
85	1	2	1	2026-06-23	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
86	1	4	1	2026-06-23	上午	2	25	0	25	20.00	正常	专家号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
87	1	3	1	2026-06-23	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
88	1	5	1	2026-06-23	下午	3	30	0	30	30.00	正常	主任号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
89	1	3	1	2026-06-24	上午	1	50	0	50	15.00	正常	周三需求稍低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
90	1	1	1	2026-06-24	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
91	1	2	1	2026-06-24	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
92	1	4	1	2026-06-24	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
93	1	2	1	2026-06-25	上午	1	50	0	50	15.00	正常	周四需求较低	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
94	1	3	1	2026-06-25	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
95	1	1	1	2026-06-25	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
96	1	5	1	2026-06-25	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
97	1	1	1	2026-06-26	上午	1	50	0	50	15.00	正常	周五需求中等	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
98	1	2	1	2026-06-26	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
99	1	5	1	2026-06-26	上午	3	30	0	30	30.00	正常	主任号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
100	1	3	1	2026-06-26	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
101	1	4	1	2026-06-26	下午	2	25	0	25	20.00	正常	专家号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
102	1	1	1	2026-06-27	上午	1	50	0	50	15.00	正常	周六仅上午出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
103	1	2	1	2026-06-28	上午	1	50	0	50	15.00	正常	节假日精简出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
104	1	3	1	2026-06-28	下午	1	50	0	50	15.00	正常	节假日出诊人数少	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
105	1	3	1	2026-06-29	上午	1	50	0	50	15.00	正常	周一需求高，号源充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
106	1	1	1	2026-06-29	上午	1	50	0	50	15.00	正常	需求旺盛，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
107	1	4	1	2026-06-29	上午	2	25	0	25	20.00	正常	专家号需求稳定	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
108	1	2	1	2026-06-29	下午	1	50	0	50	15.00	正常	需求充足，正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
109	1	5	1	2026-06-29	下午	3	30	0	30	30.00	正常	主任号按需投放	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
110	1	2	1	2026-06-30	上午	1	50	0	50	15.00	正常	周二需求较高	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
111	1	3	1	2026-06-30	上午	1	50	0	50	15.00	正常	号源充足可预约	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
112	1	5	1	2026-06-30	上午	3	30	0	30	30.00	正常	主任号正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
113	1	1	1	2026-06-30	下午	1	50	0	50	15.00	正常	需求稳定正常出诊	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
114	1	4	1	2026-06-30	下午	2	25	0	25	20.00	正常	专家号供应充足	f	\N	2026-06-18 15:49:38.813719	2026-06-18 15:49:38.813719	0
\.


--
-- Data for Name: drug_info; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.drug_info (id, drug_code, drug_name, drug_format, drug_unit, manufacturer, drug_dosage, drug_type, drug_price, mnemonic_code, creation_date, name, generic_name, brand_name, specification, dosage_form, unit, approval_number, price, stock_quantity, low_stock_threshold, storage_conditions, instructions, contraindications, adverse_reactions, status, create_time, update_time) FROM stdin;
\.


--
-- Data for Name: employee; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.employee (id, deptment_id, regist_level_id, realname, password, delmark) FROM stdin;
1	1	1	内科张医生	dev-password	0
2	1	1	内科李医生	dev-password	0
3	1	1	内科王医生	dev-password	0
4	1	2	内科赵专家	dev-password	0
5	1	3	内科刘主任	dev-password	0
6	2	1	呼吸内科周医生	dev-password	0
7	2	1	呼吸内科吴医生	dev-password	0
8	2	1	呼吸内科郑医生	dev-password	0
9	2	2	呼吸内科冯专家	dev-password	0
10	2	3	呼吸内科陈主任	dev-password	0
11	3	1	心血管内科孙医生	dev-password	0
12	3	1	心血管内科李医生	dev-password	0
13	3	1	心血管内科林医生	dev-password	0
14	3	2	心血管内科何专家	dev-password	0
15	3	3	心血管内科高主任	dev-password	0
16	4	1	消化内科马医生	dev-password	0
17	4	1	消化内科朱医生	dev-password	0
18	4	1	消化内科秦医生	dev-password	0
19	4	2	消化内科尤专家	dev-password	0
20	4	3	消化内科许主任	dev-password	0
21	5	1	神经内科施医生	dev-password	0
22	5	1	神经内科张医生	dev-password	0
23	5	1	神经内科蒋医生	dev-password	0
24	5	2	神经内科韩专家	dev-password	0
25	5	3	神经内科沈主任	dev-password	0
26	6	1	肾内科唐医生	dev-password	0
27	6	1	肾内科冯医生	dev-password	0
28	6	1	肾内科董医生	dev-password	0
29	6	2	肾内科潘专家	dev-password	0
30	6	3	肾内科姜主任	dev-password	0
31	7	1	内分泌科苏医生	dev-password	0
32	7	1	内分泌科魏医生	dev-password	0
33	7	1	内分泌科卢医生	dev-password	0
34	7	2	内分泌科崔专家	dev-password	0
35	7	3	内分泌科蔡主任	dev-password	0
36	8	1	外科丁医生	dev-password	0
37	8	1	外科沈医生	dev-password	0
38	8	1	外科徐医生	dev-password	0
39	8	2	外科蒋专家	dev-password	0
40	8	3	外科沈主任	dev-password	0
41	9	1	骨科卢医生	dev-password	0
42	9	1	骨科马医生	dev-password	0
43	9	1	骨科龚医生	dev-password	0
44	9	2	骨科秦专家	dev-password	0
45	9	3	骨科谢主任	dev-password	0
46	10	1	妇产科苏医生	dev-password	0
47	10	1	妇产科韦医生	dev-password	0
48	10	1	妇产科严医生	dev-password	0
49	10	2	妇产科卫专家	dev-password	0
50	10	3	妇产科武主任	dev-password	0
51	11	1	儿科陶医生	dev-password	0
52	11	1	儿科俞医生	dev-password	0
53	11	1	儿科任医生	dev-password	0
54	11	2	儿科袁专家	dev-password	0
55	11	3	儿科柳主任	dev-password	0
56	12	1	新生儿科毕医生	dev-password	0
57	12	1	新生儿科郝医生	dev-password	0
58	12	1	新生儿科邬医生	dev-password	0
59	12	2	新生儿科安专家	dev-password	0
60	12	3	新生儿科常主任	dev-password	0
61	13	1	眼科乐医生	dev-password	0
62	13	1	眼科于医生	dev-password	0
63	13	1	眼科傅医生	dev-password	0
64	13	2	眼科康专家	dev-password	0
65	13	3	眼科陆主任	dev-password	0
66	14	1	耳鼻咽喉科柴医生	dev-password	0
67	14	1	耳鼻咽喉科胡医生	dev-password	0
68	14	1	耳鼻咽喉科戴医生	dev-password	0
69	14	2	耳鼻咽喉科蔡专家	dev-password	0
70	14	3	耳鼻咽喉科谭主任	dev-password	0
71	15	1	口腔科舒医生	dev-password	0
72	15	1	口腔科屈医生	dev-password	0
73	15	1	口腔科项医生	dev-password	0
74	15	2	口腔科纪专家	dev-password	0
75	15	3	口腔科梁主任	dev-password	0
76	16	1	皮肤科杜医生	dev-password	0
77	16	1	皮肤科阮医生	dev-password	0
78	16	1	皮肤科贝医生	dev-password	0
79	16	2	皮肤科明专家	dev-password	0
80	16	3	皮肤科程主任	dev-password	0
81	17	1	中医科卫医生	dev-password	0
82	17	1	中医科申医生	dev-password	0
83	17	1	中医科连医生	dev-password	0
84	17	2	中医科习专家	dev-password	0
85	17	3	中医科程主任	dev-password	0
86	18	1	肿瘤科向医生	dev-password	0
87	18	1	肿瘤科丁医生	dev-password	0
88	18	1	肿瘤科茅医生	dev-password	0
89	18	2	肿瘤科左专家	dev-password	0
90	18	3	肿瘤科甘主任	dev-password	0
91	19	1	急诊科龙医生	dev-password	0
92	19	1	急诊科万医生	dev-password	0
93	19	1	急诊科柯医生	dev-password	0
94	19	2	急诊科柯专家	dev-password	0
95	19	3	急诊科支主任	dev-password	0
96	20	1	康复科管医生	dev-password	0
97	20	1	康复科蔡医生	dev-password	0
98	20	1	康复科蒙医生	dev-password	0
99	20	2	康复科应专家	dev-password	0
100	20	3	康复科丁主任	dev-password	0
101	35	\N	放射科技师王一	dev-password	0
102	35	\N	放射科技师李二	dev-password	0
103	35	\N	放射科医生张三	dev-password	0
104	36	\N	超声科技师赵四	dev-password	0
105	36	\N	超声科医生钱五	dev-password	0
106	37	\N	检验科技师孙六	dev-password	0
107	37	\N	检验科医生周七	dev-password	0
108	38	\N	输血科技师吴八	dev-password	0
109	39	\N	病理科医生郑九	dev-password	0
110	40	\N	处置室护士长冯十	dev-password	0
111	40	\N	处置室护士李一	dev-password	0
112	41	\N	内镜中心技师周二	dev-password	0
113	41	\N	内镜中心医生陈二	dev-password	0
114	42	\N	手术室护士长周三	dev-password	0
115	42	\N	手术室麻醉师李三	dev-password	0
116	44	\N	供应中心护士长王四	dev-password	0
117	45	\N	药房药师张五	dev-password	0
118	45	\N	药房药师赵六	dev-password	0
\.


--
-- Data for Name: expense_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.expense_record (id, register_id, patient_id, patient_name, category_id, category_name, item_id, item_name, item_code, quantity, unit_price, total_amount, status, pay_time, refund_time, operator_id, operator_name, remark, create_time) FROM stdin;
\.


--
-- Data for Name: inspection_request; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.inspection_request (id, register_id, medical_technology_id, inspection_info, inspection_position, creation_time, inspection_employee_id, inputinspection_employee_id, inspection_time, inspection_result, inspection_state, inspection_remark) FROM stdin;
\.


--
-- Data for Name: leave_request; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.leave_request (id, physician_id, leave_date, time_slot, leave_type, reason, ai_parsed_date, ai_parsed_slot, ai_confidence, status, approver_id, approval_time, auto_processed, create_time, delmark) FROM stdin;
\.


--
-- Data for Name: medical_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_record (id, register_id, readme, present, present_treat, history, allergy, physique, proposal, careful, diagnosis, cure, preliminary_diagnosis) FROM stdin;
2	2	不适待查	我在昨日突然畏寒寒颤，全身皮肤肌肉酸痛，触碰疼，运动疼，全身乏力，有低烧37.9度，其他生命体征正常，我个人怀疑是病毒性感染。服用抗病毒口服液后睡了一晚上后，情况明显好转，不再发烧，基本不痛，但是头依旧运动时会疼痛，还有一定乏力情况，且面部发黄发黑，缺乏气色。	无治疗措施	既往体健	无过敏史	既往体健		\N	\N	\N	\N
\.


--
-- Data for Name: medical_record_disease; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_record_disease (medical_record_id, disease_id) FROM stdin;
\.


--
-- Data for Name: medical_technology; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medical_technology (id, tech_code, tech_name, tech_format, tech_price, tech_type, price_type, deptment_id, name, code, type, department_id, department_name, price, specimen_type, container, instructions, preparation, turnaround_time, status, description, create_time, update_time, ai_category_code) FROM stdin;
2	ECG	心电图	常规	50.00	check	检查费	3	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 15:36:04.775138	2026-06-18 15:36:04.775138	general_check
3	USABD	腹部超声	常规	120.00	check	检查费	3	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 15:36:04.775138	2026-06-18 15:36:04.775138	general_check
4	XCG	血常规	次	50.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:02:37.33732	2026-06-18 16:02:37.33732	\N
5	NCG	尿常规	次	50.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:02:54.575593	2026-06-18 16:02:54.575593	\N
6	GGN	肝功能	次	100.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:03:07.012209	2026-06-18 16:03:07.012209	\N
7	SGN	肾功能	次	100.00	check	检查费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:03:19.305814	2026-06-18 16:03:19.305814	\N
8	JZXGN	甲状腺功能	次	100.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:03:35.905738	2026-06-18 16:03:35.905738	\N
9	NXGN	凝血功能	次	100.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:03:53.935796	2026-06-18 16:03:53.935796	\N
10	FBCG	粪便常规	次	100.00	inspection	检验费	\N	\N	\N	\N	\N	\N	0.00	\N	\N	\N	\N	\N	1	\N	2026-06-18 16:04:10.389123	2026-06-18 16:04:10.389123	\N
\.


--
-- Data for Name: patient; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.patient (id, real_name, id_card, gender, birthdate, phone, avatar, home_address, allergy_history, delmark, create_time, update_time, account_balance) FROM stdin;
2	陈赞	442000200412121212	男	2004-12-12	18911111111	\N	\N	\N	1	2026-06-18 15:43:18.439725	2026-06-18 15:45:06.536947	300.00
\.


--
-- Data for Name: patient_balance_transaction; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.patient_balance_transaction (id, transaction_no, patient_id, transaction_type, amount, balance_before, balance_after, business_type, business_id, operator_id, operator_name, remark, transaction_time, create_time) FROM stdin;
1	RC178176870654266C70D54	2	RECHARGE	300.00	0.00	300.00	RECHARGE	\N	\N	患者自助	账户充值	2026-06-18 15:45:06.543334	2026-06-18 15:45:06.536947
\.


--
-- Data for Name: prescription; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.prescription (id, register_id, drug_id, drug_usage, drug_number, creation_time, drug_state) FROM stdin;
\.


--
-- Data for Name: regist_level; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.regist_level (id, regist_code, regist_name, regist_fee, regist_quota, sequence_no, delmark) FROM stdin;
1	PT	普通号	5.00	50	1	0
2	ZJ	专家号	15.00	30	2	0
3	ZR	主任医师号	30.00	15	3	0
\.


--
-- Data for Name: register; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.register (id, case_number, real_name, gender, card_number, birthdate, age, age_type, home_address, visit_date, noon, deptment_id, employee_id, regist_level_id, settle_category_id, is_book, regist_method, regist_money, visit_state, patient_id, scheduling_id, check_in_time) FROM stdin;
2	BL20260618001	陈赞	男	442000200412121212	2004-12-12	21	年	\N	2026-06-18 15:49:56.707406	下午	1	1	2	1	否	线上	15.00	2	2	\N	2026-06-18 15:49:56.707406
\.


--
-- Data for Name: result_form_category; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.result_form_category (category_code, category_name, description) FROM stdin;
general_check	通用检查	普通检查项目的默认结果录入模板
imaging_ct	影像CT	CT 影像检查结构化报告模板
general_lab	通用检验	普通检验项目的默认结果录入模板
\.


--
-- Data for Name: result_form_field; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.result_form_field (id, owner_type, owner_key, field_key, field_label, field_type, required, sort_order, placeholder, max_length, options_json) FROM stdin;
1	category	general_check	checkResult	检查结果	textarea	t	1	请填写检查结果	\N	\N
2	category	general_check	checkRemark	备注	textarea	f	2	可选备注	\N	\N
3	category	imaging_ct	findings	所见	textarea	t	1	影像所见描述	\N	\N
4	category	imaging_ct	impression	印象	textarea	t	2	影像印象	\N	\N
5	category	imaging_ct	conclusion	结论	textarea	t	3	诊断结论	\N	\N
6	tech_extension	1	contrastReaction	造影剂反应	text	f	10	如：无、轻微恶心等	\N	\N
7	category	general_lab	inspectionResult	检验结果	textarea	t	1	请填写检验结论或汇总	\N	\N
8	category	general_lab	inspectionRemark	备注	textarea	f	2	可选备注	\N	\N
\.


--
-- Data for Name: schedule_adjust_log; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.schedule_adjust_log (id, schedule_id, field_name, old_value, new_value, adjust_type, adjust_by, adjust_time, remark, delmark) FROM stdin;
\.


--
-- Data for Name: schedule_adjust_request; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.schedule_adjust_request (id, schedule_id, adjust_type, old_physician_id, new_physician_id, old_status, new_status, old_quota, new_quota, reason, ai_suggestion, affect_patients, triggered_by, status, confirmed_by, confirm_time, confirm_remark, create_time, delmark) FROM stdin;
\.


--
-- Data for Name: schedule_plan; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.schedule_plan (id, plan_name, department_id, plan_month, status, ai_generated, ai_version, total_schedules, total_quota, created_by, created_time, published_time, published_by, delmark) FROM stdin;
1	内科2026-06排班	1	2026-06	草稿	t	1	114	4890	1	2026-06-18 15:49:38.813719	\N	\N	0
\.


--
-- Data for Name: settle_category; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.settle_category (id, settle_code, settle_name, sequence_no, delmark) FROM stdin;
1	ZF	自费	1	0
2	YB	医保	2	0
\.


--
-- Data for Name: triage_desk_record; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.triage_desk_record (id, patient_id, patient_name, patient_phone, symptoms, ai_triage_result, recommended_department_id, recommended_department, recommended_physician_id, recommended_physician_name, risk_level, ai_analysis, status, confirmed_department_id, confirmed_department, confirmed_physician_id, confirmed_physician_name, operator_id, operator_name, confirm_remark, create_time, confirm_time) FROM stdin;
\.


--
-- Data for Name: user_patient_managed; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_patient_managed (user_id, patient_id, create_time, relation) FROM stdin;
7	2	2026-06-18 15:43:18.439725	本人
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, username, password, real_name, email, phone, id_card, gender, birthdate, avatar, patient_id, status, user_type, remark, create_time, update_time) FROM stdin;
1	admin	admin123	系统管理员	\N	\N	\N	\N	\N	\N	\N	1	1	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
2	doctor1	doctor123	张医生	\N	\N	\N	\N	\N	\N	\N	1	2	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
3	reg001	reg123	李收费	\N	\N	\N	\N	\N	\N	\N	1	3	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
4	medtech01	medtech123	王技师	\N	\N	\N	\N	\N	\N	\N	1	4	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
5	pharma01	pharma123	赵药师	\N	\N	\N	\N	\N	\N	\N	1	5	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
6	patient001	patient123	患者小明	\N	\N	\N	\N	\N	\N	\N	1	6	\N	2026-06-18 15:34:48.194677	2026-06-18 15:34:48.194677
7	zander	123456	陈赞	\N	18911111111	\N	\N	\N	\N	\N	1	6	\N	2026-06-18 15:43:18.439725	2026-06-18 15:43:18.439725
\.


--
-- Name: ai_consultation_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_consultation_record_id_seq', 1, false);


--
-- Name: ai_diagnosis_suggestion_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_diagnosis_suggestion_id_seq', 1, false);


--
-- Name: ai_exam_analysis_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_exam_analysis_id_seq', 1, false);


--
-- Name: ai_exam_suggestion_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_exam_suggestion_id_seq', 5, true);


--
-- Name: ai_follow_up_plan_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_follow_up_plan_id_seq', 1, false);


--
-- Name: ai_follow_up_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_follow_up_record_id_seq', 1, false);


--
-- Name: ai_medical_record_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_medical_record_log_id_seq', 5, true);


--
-- Name: ai_prescription_review_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_prescription_review_id_seq', 1, false);


--
-- Name: ai_triage_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.ai_triage_record_id_seq', 1, true);


--
-- Name: check_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.check_request_id_seq', 1, true);


--
-- Name: department_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.department_id_seq', 45, true);


--
-- Name: disease_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.disease_id_seq', 1, true);


--
-- Name: disposal_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.disposal_request_id_seq', 1, true);


--
-- Name: doctor_schedule_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.doctor_schedule_id_seq', 114, true);


--
-- Name: drug_info_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.drug_info_id_seq', 1, true);


--
-- Name: employee_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.employee_id_seq', 118, true);


--
-- Name: expense_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.expense_record_id_seq', 1, false);


--
-- Name: inspection_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.inspection_request_id_seq', 5, true);


--
-- Name: leave_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.leave_request_id_seq', 1, false);


--
-- Name: medical_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_record_id_seq', 2, true);


--
-- Name: medical_technology_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.medical_technology_id_seq', 10, true);


--
-- Name: patient_balance_transaction_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.patient_balance_transaction_id_seq', 1, true);


--
-- Name: patient_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.patient_id_seq', 2, true);


--
-- Name: prescription_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.prescription_id_seq', 1, true);


--
-- Name: regist_level_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.regist_level_id_seq', 3, true);


--
-- Name: register_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.register_id_seq', 2, true);


--
-- Name: result_form_field_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.result_form_field_id_seq', 10, true);


--
-- Name: schedule_adjust_log_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.schedule_adjust_log_id_seq', 1, false);


--
-- Name: schedule_adjust_request_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.schedule_adjust_request_id_seq', 1, false);


--
-- Name: schedule_plan_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.schedule_plan_id_seq', 1, true);


--
-- Name: settle_category_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.settle_category_id_seq', 2, true);


--
-- Name: triage_desk_record_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.triage_desk_record_id_seq', 1, false);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.users_id_seq', 7, true);


--
-- Name: ai_consultation_record ai_consultation_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_consultation_record
    ADD CONSTRAINT ai_consultation_record_pkey PRIMARY KEY (id);


--
-- Name: ai_diagnosis_suggestion ai_diagnosis_suggestion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT ai_diagnosis_suggestion_pkey PRIMARY KEY (id);


--
-- Name: ai_exam_analysis ai_exam_analysis_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT ai_exam_analysis_pkey PRIMARY KEY (id);


--
-- Name: ai_exam_suggestion ai_exam_suggestion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT ai_exam_suggestion_pkey PRIMARY KEY (id);


--
-- Name: ai_follow_up_plan ai_follow_up_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT ai_follow_up_plan_pkey PRIMARY KEY (id);


--
-- Name: ai_follow_up_record ai_follow_up_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT ai_follow_up_record_pkey PRIMARY KEY (id);


--
-- Name: ai_medical_record_log ai_medical_record_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT ai_medical_record_log_pkey PRIMARY KEY (id);


--
-- Name: ai_prescription_review ai_prescription_review_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT ai_prescription_review_pkey PRIMARY KEY (id);


--
-- Name: ai_triage_record ai_triage_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT ai_triage_record_pkey PRIMARY KEY (id);


--
-- Name: check_request check_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT check_request_pkey PRIMARY KEY (id);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: disease disease_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disease
    ADD CONSTRAINT disease_pkey PRIMARY KEY (id);


--
-- Name: disposal_request disposal_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT disposal_request_pkey PRIMARY KEY (id);


--
-- Name: doctor_schedule doctor_schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT doctor_schedule_pkey PRIMARY KEY (id);


--
-- Name: drug_info drug_info_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.drug_info
    ADD CONSTRAINT drug_info_pkey PRIMARY KEY (id);


--
-- Name: employee employee_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT employee_pkey PRIMARY KEY (id);


--
-- Name: expense_record expense_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expense_record
    ADD CONSTRAINT expense_record_pkey PRIMARY KEY (id);


--
-- Name: inspection_request inspection_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT inspection_request_pkey PRIMARY KEY (id);


--
-- Name: leave_request leave_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT leave_request_pkey PRIMARY KEY (id);


--
-- Name: medical_record_disease medical_record_disease_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT medical_record_disease_pkey PRIMARY KEY (medical_record_id, disease_id);


--
-- Name: medical_record medical_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT medical_record_pkey PRIMARY KEY (id);


--
-- Name: medical_technology medical_technology_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT medical_technology_pkey PRIMARY KEY (id);


--
-- Name: patient_balance_transaction patient_balance_transaction_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient_balance_transaction
    ADD CONSTRAINT patient_balance_transaction_pkey PRIMARY KEY (id);


--
-- Name: patient patient_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (id);


--
-- Name: prescription prescription_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT prescription_pkey PRIMARY KEY (id);


--
-- Name: regist_level regist_level_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regist_level
    ADD CONSTRAINT regist_level_pkey PRIMARY KEY (id);


--
-- Name: register register_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT register_pkey PRIMARY KEY (id);


--
-- Name: result_form_category result_form_category_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.result_form_category
    ADD CONSTRAINT result_form_category_pkey PRIMARY KEY (category_code);


--
-- Name: result_form_field result_form_field_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.result_form_field
    ADD CONSTRAINT result_form_field_pkey PRIMARY KEY (id);


--
-- Name: schedule_adjust_log schedule_adjust_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_log
    ADD CONSTRAINT schedule_adjust_log_pkey PRIMARY KEY (id);


--
-- Name: schedule_adjust_request schedule_adjust_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_request
    ADD CONSTRAINT schedule_adjust_request_pkey PRIMARY KEY (id);


--
-- Name: schedule_plan schedule_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_plan
    ADD CONSTRAINT schedule_plan_pkey PRIMARY KEY (id);


--
-- Name: settle_category settle_category_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settle_category
    ADD CONSTRAINT settle_category_pkey PRIMARY KEY (id);


--
-- Name: triage_desk_record triage_desk_record_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.triage_desk_record
    ADD CONSTRAINT triage_desk_record_pkey PRIMARY KEY (id);


--
-- Name: department uk_department_dept_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT uk_department_dept_code UNIQUE (dept_code);


--
-- Name: disease uk_disease_icd; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disease
    ADD CONSTRAINT uk_disease_icd UNIQUE (diseaseicd);


--
-- Name: drug_info uk_drug_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.drug_info
    ADD CONSTRAINT uk_drug_code UNIQUE (drug_code);


--
-- Name: medical_record uk_medical_record_register; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT uk_medical_record_register UNIQUE (register_id);


--
-- Name: medical_technology uk_medtech_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT uk_medtech_code UNIQUE (tech_code);


--
-- Name: patient_balance_transaction uk_patient_balance_transaction_no; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient_balance_transaction
    ADD CONSTRAINT uk_patient_balance_transaction_no UNIQUE (transaction_no);


--
-- Name: patient uk_patient_id_card; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient
    ADD CONSTRAINT uk_patient_id_card UNIQUE (id_card);


--
-- Name: regist_level uk_regist_level_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.regist_level
    ADD CONSTRAINT uk_regist_level_code UNIQUE (regist_code);


--
-- Name: result_form_field uk_result_form_field_owner_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.result_form_field
    ADD CONSTRAINT uk_result_form_field_owner_key UNIQUE (owner_type, owner_key, field_key);


--
-- Name: settle_category uk_settle_category_code; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.settle_category
    ADD CONSTRAINT uk_settle_category_code UNIQUE (settle_code);


--
-- Name: users uk_users_id_card; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_id_card UNIQUE (id_card);


--
-- Name: user_patient_managed user_patient_managed_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_patient_managed
    ADD CONSTRAINT user_patient_managed_pkey PRIMARY KEY (user_id, patient_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_ai_consult_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_consult_register_id ON public.ai_consultation_record USING btree (register_id);


--
-- Name: idx_ai_consult_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_consult_state ON public.ai_consultation_record USING btree (consultation_state);


--
-- Name: idx_ai_diagnosis_disease_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_diagnosis_disease_id ON public.ai_diagnosis_suggestion USING btree (disease_id);


--
-- Name: idx_ai_diagnosis_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_diagnosis_register_id ON public.ai_diagnosis_suggestion USING btree (register_id);


--
-- Name: idx_ai_exam_analysis_check_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_exam_analysis_check_id ON public.ai_exam_analysis USING btree (check_request_id);


--
-- Name: idx_ai_exam_analysis_inspection_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_exam_analysis_inspection_id ON public.ai_exam_analysis USING btree (inspection_request_id);


--
-- Name: idx_ai_exam_analysis_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_exam_analysis_register_id ON public.ai_exam_analysis USING btree (register_id);


--
-- Name: idx_ai_exam_analysis_risk; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_exam_analysis_risk ON public.ai_exam_analysis USING btree (risk_level);


--
-- Name: idx_ai_exam_sug_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_exam_sug_register_id ON public.ai_exam_suggestion USING btree (register_id);


--
-- Name: idx_ai_followup_planned_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_followup_planned_date ON public.ai_follow_up_plan USING btree (planned_date);


--
-- Name: idx_ai_followup_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_followup_register_id ON public.ai_follow_up_plan USING btree (register_id);


--
-- Name: idx_ai_followup_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_followup_status ON public.ai_follow_up_plan USING btree (plan_status);


--
-- Name: idx_ai_fur_plan_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_fur_plan_id ON public.ai_follow_up_record USING btree (follow_up_plan_id);


--
-- Name: idx_ai_fur_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_fur_register_id ON public.ai_follow_up_record USING btree (register_id);


--
-- Name: idx_ai_mrlog_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_mrlog_register_id ON public.ai_medical_record_log USING btree (register_id);


--
-- Name: idx_ai_review_prescription_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_review_prescription_id ON public.ai_prescription_review USING btree (prescription_id);


--
-- Name: idx_ai_review_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_review_register_id ON public.ai_prescription_review USING btree (register_id);


--
-- Name: idx_ai_review_result; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_review_result ON public.ai_prescription_review USING btree (review_result);


--
-- Name: idx_ai_triage_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_triage_register_id ON public.ai_triage_record USING btree (register_id);


--
-- Name: idx_ai_triage_risk_level; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_triage_risk_level ON public.ai_triage_record USING btree (risk_level);


--
-- Name: idx_ai_triage_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_triage_time ON public.ai_triage_record USING btree (triage_time);


--
-- Name: idx_check_request_medtech_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_check_request_medtech_id ON public.check_request USING btree (medical_technology_id);


--
-- Name: idx_check_request_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_check_request_register_id ON public.check_request USING btree (register_id);


--
-- Name: idx_check_request_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_check_request_state ON public.check_request USING btree (check_state);


--
-- Name: idx_disease_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_disease_category ON public.disease USING btree (disease_category);


--
-- Name: idx_disease_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_disease_name ON public.disease USING btree (disease_name);


--
-- Name: idx_disposal_request_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_disposal_request_register_id ON public.disposal_request USING btree (register_id);


--
-- Name: idx_disposal_request_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_disposal_request_state ON public.disposal_request USING btree (disposal_state);


--
-- Name: idx_drug_info_low_stock; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_info_low_stock ON public.drug_info USING btree (status, stock_quantity);


--
-- Name: idx_drug_info_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_info_name ON public.drug_info USING btree (name);


--
-- Name: idx_drug_info_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_info_status ON public.drug_info USING btree (status);


--
-- Name: idx_drug_mnemonic; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_mnemonic ON public.drug_info USING btree (mnemonic_code);


--
-- Name: idx_drug_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_name ON public.drug_info USING btree (drug_name);


--
-- Name: idx_drug_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_drug_type ON public.drug_info USING btree (drug_type);


--
-- Name: idx_ds_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ds_date ON public.doctor_schedule USING btree (work_date);


--
-- Name: idx_ds_department; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ds_department ON public.doctor_schedule USING btree (department_id);


--
-- Name: idx_ds_physician; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ds_physician ON public.doctor_schedule USING btree (physician_id);


--
-- Name: idx_ds_plan; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ds_plan ON public.doctor_schedule USING btree (plan_id);


--
-- Name: idx_ds_unique; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX idx_ds_unique ON public.doctor_schedule USING btree (work_date, physician_id, time_slot) WHERE (delmark = 0);


--
-- Name: idx_employee_deptment_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employee_deptment_id ON public.employee USING btree (deptment_id);


--
-- Name: idx_employee_regist_level_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_employee_regist_level_id ON public.employee USING btree (regist_level_id);


--
-- Name: idx_expense_record_patient_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expense_record_patient_id ON public.expense_record USING btree (patient_id);


--
-- Name: idx_expense_record_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expense_record_register_id ON public.expense_record USING btree (register_id);


--
-- Name: idx_expense_record_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expense_record_status ON public.expense_record USING btree (status);


--
-- Name: idx_inspection_request_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_inspection_request_register_id ON public.inspection_request USING btree (register_id);


--
-- Name: idx_inspection_request_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_inspection_request_state ON public.inspection_request USING btree (inspection_state);


--
-- Name: idx_lr_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_lr_date ON public.leave_request USING btree (leave_date);


--
-- Name: idx_lr_physician; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_lr_physician ON public.leave_request USING btree (physician_id);


--
-- Name: idx_lr_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_lr_status ON public.leave_request USING btree (status);


--
-- Name: idx_medtech_dept_new; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_dept_new ON public.medical_technology USING btree (department_id);


--
-- Name: idx_medtech_deptment_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_deptment_id ON public.medical_technology USING btree (deptment_id);


--
-- Name: idx_medtech_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_name ON public.medical_technology USING btree (name);


--
-- Name: idx_medtech_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_status ON public.medical_technology USING btree (status);


--
-- Name: idx_medtech_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_type ON public.medical_technology USING btree (tech_type);


--
-- Name: idx_medtech_type_new; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_medtech_type_new ON public.medical_technology USING btree (type);


--
-- Name: idx_patient_account_balance; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_patient_account_balance ON public.patient USING btree (account_balance);


--
-- Name: idx_patient_balance_transaction_business; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_patient_balance_transaction_business ON public.patient_balance_transaction USING btree (patient_id, transaction_type, business_type, business_id);


--
-- Name: idx_patient_balance_transaction_patient_time; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_patient_balance_transaction_patient_time ON public.patient_balance_transaction USING btree (patient_id, transaction_time DESC);


--
-- Name: idx_patient_id_card; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_patient_id_card ON public.patient USING btree (id_card);


--
-- Name: idx_prescription_drug_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prescription_drug_id ON public.prescription USING btree (drug_id);


--
-- Name: idx_prescription_register_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prescription_register_id ON public.prescription USING btree (register_id);


--
-- Name: idx_prescription_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prescription_state ON public.prescription USING btree (drug_state);


--
-- Name: idx_register_case_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_case_number ON public.register USING btree (case_number);


--
-- Name: idx_register_deptment_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_deptment_id ON public.register USING btree (deptment_id);


--
-- Name: idx_register_employee_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_employee_id ON public.register USING btree (employee_id);


--
-- Name: idx_register_patient_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_patient_id ON public.register USING btree (patient_id);


--
-- Name: idx_register_real_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_real_name ON public.register USING btree (real_name);


--
-- Name: idx_register_scheduling_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_scheduling_id ON public.register USING btree (scheduling_id);


--
-- Name: idx_register_visit_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_visit_date ON public.register USING btree (visit_date);


--
-- Name: idx_register_visit_state; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_register_visit_state ON public.register USING btree (visit_state);


--
-- Name: idx_result_form_field_owner; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_result_form_field_owner ON public.result_form_field USING btree (owner_type, owner_key);


--
-- Name: idx_sal_adjust_by; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sal_adjust_by ON public.schedule_adjust_log USING btree (adjust_by);


--
-- Name: idx_sal_schedule; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sal_schedule ON public.schedule_adjust_log USING btree (schedule_id);


--
-- Name: idx_sar_schedule; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sar_schedule ON public.schedule_adjust_request USING btree (schedule_id);


--
-- Name: idx_sar_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sar_status ON public.schedule_adjust_request USING btree (status);


--
-- Name: idx_sar_triggered_by; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sar_triggered_by ON public.schedule_adjust_request USING btree (triggered_by);


--
-- Name: idx_sp_dept_month; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sp_dept_month ON public.schedule_plan USING btree (department_id, plan_month);


--
-- Name: idx_sp_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_sp_status ON public.schedule_plan USING btree (status);


--
-- Name: idx_triage_desk_patient_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_triage_desk_patient_id ON public.triage_desk_record USING btree (patient_id);


--
-- Name: idx_triage_desk_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_triage_desk_status ON public.triage_desk_record USING btree (status);


--
-- Name: idx_upm_patient; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_upm_patient ON public.user_patient_managed USING btree (patient_id);


--
-- Name: idx_upm_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_upm_user ON public.user_patient_managed USING btree (user_id);


--
-- Name: idx_users_id_card; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_id_card ON public.users USING btree (id_card);


--
-- Name: idx_users_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_status ON public.users USING btree (status);


--
-- Name: idx_users_user_type; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_user_type ON public.users USING btree (user_type);


--
-- Name: idx_users_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_users_username ON public.users USING btree (username);


--
-- Name: uk_patient_balance_transaction_business_unique; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_patient_balance_transaction_business_unique ON public.patient_balance_transaction USING btree (patient_id, transaction_type, business_type, business_id) WHERE ((business_type IS NOT NULL) AND (business_id IS NOT NULL));


--
-- Name: ai_consultation_record fk_ai_consult_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_consultation_record
    ADD CONSTRAINT fk_ai_consult_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_diagnosis_suggestion fk_ai_diagnosis_disease; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT fk_ai_diagnosis_disease FOREIGN KEY (disease_id) REFERENCES public.disease(id) ON DELETE SET NULL;


--
-- Name: ai_diagnosis_suggestion fk_ai_diagnosis_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_diagnosis_suggestion
    ADD CONSTRAINT fk_ai_diagnosis_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_check; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_check FOREIGN KEY (check_request_id) REFERENCES public.check_request(id) ON DELETE SET NULL;


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_inspection; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_inspection FOREIGN KEY (inspection_request_id) REFERENCES public.inspection_request(id) ON DELETE SET NULL;


--
-- Name: ai_exam_analysis fk_ai_exam_analysis_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_analysis
    ADD CONSTRAINT fk_ai_exam_analysis_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_exam_suggestion fk_ai_exam_sug_medtech; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT fk_ai_exam_sug_medtech FOREIGN KEY (tech_id) REFERENCES public.medical_technology(id);


--
-- Name: ai_exam_suggestion fk_ai_exam_sug_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_exam_suggestion
    ADD CONSTRAINT fk_ai_exam_sug_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_follow_up_plan fk_ai_followup_prescription; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT fk_ai_followup_prescription FOREIGN KEY (prescription_id) REFERENCES public.prescription(id) ON DELETE SET NULL;


--
-- Name: ai_follow_up_record fk_ai_followup_record_plan; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT fk_ai_followup_record_plan FOREIGN KEY (follow_up_plan_id) REFERENCES public.ai_follow_up_plan(id);


--
-- Name: ai_follow_up_record fk_ai_followup_record_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_record
    ADD CONSTRAINT fk_ai_followup_record_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_follow_up_plan fk_ai_followup_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_follow_up_plan
    ADD CONSTRAINT fk_ai_followup_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_medical_record_log fk_ai_mrlog_medical_record; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT fk_ai_mrlog_medical_record FOREIGN KEY (medical_record_id) REFERENCES public.medical_record(id) ON DELETE SET NULL;


--
-- Name: ai_medical_record_log fk_ai_mrlog_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_medical_record_log
    ADD CONSTRAINT fk_ai_mrlog_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_prescription_review fk_ai_review_prescription; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT fk_ai_review_prescription FOREIGN KEY (prescription_id) REFERENCES public.prescription(id);


--
-- Name: ai_prescription_review fk_ai_review_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_prescription_review
    ADD CONSTRAINT fk_ai_review_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: ai_triage_record fk_ai_triage_dept; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_dept FOREIGN KEY (recommend_dept_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: ai_triage_record fk_ai_triage_doctor; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_doctor FOREIGN KEY (recommend_doctor_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: ai_triage_record fk_ai_triage_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_triage_record
    ADD CONSTRAINT fk_ai_triage_register FOREIGN KEY (register_id) REFERENCES public.register(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_employee FOREIGN KEY (check_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_input_employee FOREIGN KEY (inputcheck_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: check_request fk_check_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: check_request fk_check_request_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.check_request
    ADD CONSTRAINT fk_check_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: disposal_request fk_disposal_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_employee FOREIGN KEY (disposal_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: disposal_request fk_disposal_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_input_employee FOREIGN KEY (inputdisposal_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: disposal_request fk_disposal_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: disposal_request fk_disposal_request_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.disposal_request
    ADD CONSTRAINT fk_disposal_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: doctor_schedule fk_ds_department; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT fk_ds_department FOREIGN KEY (department_id) REFERENCES public.department(id);


--
-- Name: doctor_schedule fk_ds_physician; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT fk_ds_physician FOREIGN KEY (physician_id) REFERENCES public.employee(id);


--
-- Name: doctor_schedule fk_ds_plan; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT fk_ds_plan FOREIGN KEY (plan_id) REFERENCES public.schedule_plan(id);


--
-- Name: doctor_schedule fk_ds_regist_level; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.doctor_schedule
    ADD CONSTRAINT fk_ds_regist_level FOREIGN KEY (regist_level_id) REFERENCES public.regist_level(id);


--
-- Name: employee fk_employee_department; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT fk_employee_department FOREIGN KEY (deptment_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: employee fk_employee_regist_level; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee
    ADD CONSTRAINT fk_employee_regist_level FOREIGN KEY (regist_level_id) REFERENCES public.regist_level(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_employee FOREIGN KEY (inspection_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_input_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_input_employee FOREIGN KEY (inputinspection_employee_id) REFERENCES public.employee(id) ON DELETE SET NULL;


--
-- Name: inspection_request fk_inspection_request_medtech; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_medtech FOREIGN KEY (medical_technology_id) REFERENCES public.medical_technology(id);


--
-- Name: inspection_request fk_inspection_request_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inspection_request
    ADD CONSTRAINT fk_inspection_request_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: leave_request fk_lr_physician; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT fk_lr_physician FOREIGN KEY (physician_id) REFERENCES public.employee(id);


--
-- Name: medical_record fk_medical_record_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record
    ADD CONSTRAINT fk_medical_record_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: medical_technology fk_medtech_department; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_technology
    ADD CONSTRAINT fk_medtech_department FOREIGN KEY (deptment_id) REFERENCES public.department(id) ON DELETE SET NULL;


--
-- Name: medical_record_disease fk_mrd_disease; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT fk_mrd_disease FOREIGN KEY (disease_id) REFERENCES public.disease(id);


--
-- Name: medical_record_disease fk_mrd_medical_record; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medical_record_disease
    ADD CONSTRAINT fk_mrd_medical_record FOREIGN KEY (medical_record_id) REFERENCES public.medical_record(id) ON DELETE CASCADE;


--
-- Name: patient_balance_transaction fk_patient_balance_transaction_patient; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.patient_balance_transaction
    ADD CONSTRAINT fk_patient_balance_transaction_patient FOREIGN KEY (patient_id) REFERENCES public.patient(id);


--
-- Name: prescription fk_prescription_drug; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT fk_prescription_drug FOREIGN KEY (drug_id) REFERENCES public.drug_info(id);


--
-- Name: prescription fk_prescription_register; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prescription
    ADD CONSTRAINT fk_prescription_register FOREIGN KEY (register_id) REFERENCES public.register(id);


--
-- Name: register fk_register_department; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_department FOREIGN KEY (deptment_id) REFERENCES public.department(id);


--
-- Name: register fk_register_employee; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_employee FOREIGN KEY (employee_id) REFERENCES public.employee(id);


--
-- Name: register fk_register_regist_level; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_regist_level FOREIGN KEY (regist_level_id) REFERENCES public.regist_level(id);


--
-- Name: register fk_register_settle_category; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.register
    ADD CONSTRAINT fk_register_settle_category FOREIGN KEY (settle_category_id) REFERENCES public.settle_category(id) ON DELETE SET NULL;


--
-- Name: schedule_adjust_log fk_sal_schedule; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_log
    ADD CONSTRAINT fk_sal_schedule FOREIGN KEY (schedule_id) REFERENCES public.doctor_schedule(id);


--
-- Name: schedule_adjust_request fk_sar_schedule; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_adjust_request
    ADD CONSTRAINT fk_sar_schedule FOREIGN KEY (schedule_id) REFERENCES public.doctor_schedule(id);


--
-- Name: schedule_plan fk_sp_department; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.schedule_plan
    ADD CONSTRAINT fk_sp_department FOREIGN KEY (department_id) REFERENCES public.department(id);


--
-- Name: user_patient_managed fk_upm_patient; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_patient_managed
    ADD CONSTRAINT fk_upm_patient FOREIGN KEY (patient_id) REFERENCES public.patient(id) ON DELETE CASCADE;


--
-- Name: user_patient_managed fk_upm_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_patient_managed
    ADD CONSTRAINT fk_upm_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users fk_users_patient; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_patient FOREIGN KEY (patient_id) REFERENCES public.patient(id) ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--

\unrestrict Y29JjHc3qShKGlLuMHvxJOD7SSqFNVOtbDrct4LIzs8mJLx2RAqsOUHUTYt27Li

--
-- PostgreSQL database cluster dump complete
--

