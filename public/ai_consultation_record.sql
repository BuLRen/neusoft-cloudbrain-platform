create table ai_consultation_record
(
    id                 serial
        primary key,
    register_id        integer                                               not null
        constraint fk_ai_consult_register
            references register,
    round_number       integer      default 1                                not null,
    ai_question        text,
    patient_answer     text,
    consultation_state varchar(16)  default 'in_progress'::character varying not null
        constraint chk_ai_consult_state
            check ((consultation_state)::text = ANY
                   ((ARRAY ['in_progress'::character varying, 'completed'::character varying, 'cancelled'::character varying])::text[])),
    chief_complaint    varchar(512) default NULL::character varying,
    symptom_duration   varchar(128) default NULL::character varying,
    history_summary    text,
    allergy_summary    text,
    medication_summary text,
    ai_summary         text,
    suggested_exam     text,
    creation_time      timestamp    default CURRENT_TIMESTAMP,
    completion_time    timestamp,
    model_id           varchar(64)  default NULL::character varying
);

comment on table ai_consultation_record is 'AI预问诊记录表（多轮对话）';

alter table ai_consultation_record
    owner to postgres;

create index idx_ai_consult_register_id
    on ai_consultation_record (register_id);

create index idx_ai_consult_state
    on ai_consultation_record (consultation_state);

