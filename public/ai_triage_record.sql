create table ai_triage_record
(
    id                    serial
        primary key,
    patient_name          varchar(64) default NULL::character varying,
    patient_age           integer,
    patient_gender        varchar(6)  default NULL::character varying
        constraint chk_ai_triage_gender
            check ((patient_gender IS NULL) OR
                   ((patient_gender)::text = ANY ((ARRAY ['男'::character varying, '女'::character varying])::text[]))),
    symptom_description   text                                            not null,
    recommend_dept_id     integer
        constraint fk_ai_triage_dept
            references department
            on delete set null,
    recommend_dept_name   varchar(64) default NULL::character varying,
    recommend_doctor_id   integer
        constraint fk_ai_triage_doctor
            references employee
            on delete set null,
    recommend_doctor_name varchar(64) default NULL::character varying,
    risk_level            varchar(16) default 'normal'::character varying not null
        constraint chk_ai_triage_risk
            check ((risk_level)::text = ANY
                   ((ARRAY ['normal'::character varying, 'urgent'::character varying, 'critical'::character varying])::text[])),
    is_priority           smallint    default 0                           not null
        constraint chk_ai_triage_priority
            check (is_priority = ANY (ARRAY [0, 1])),
    ai_analysis           text,
    register_id           integer
        constraint fk_ai_triage_register
            references register
            on delete set null,
    triage_time           timestamp   default CURRENT_TIMESTAMP,
    model_id              varchar(64) default NULL::character varying
);

comment on table ai_triage_record is 'AI导诊记录表';

comment on column ai_triage_record.register_id is '关联挂号ID，患者实际挂号后回填';

alter table ai_triage_record
    owner to postgres;

create index idx_ai_triage_register_id
    on ai_triage_record (register_id);

create index idx_ai_triage_risk_level
    on ai_triage_record (risk_level);

create index idx_ai_triage_time
    on ai_triage_record (triage_time);

