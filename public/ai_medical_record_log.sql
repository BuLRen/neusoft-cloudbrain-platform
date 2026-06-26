create table ai_medical_record_log
(
    id                  serial
        primary key,
    register_id         integer               not null
        constraint fk_ai_mrlog_register
            references register,
    medical_record_id   integer
        constraint fk_ai_mrlog_medical_record
            references medical_record
            on delete set null,
    source_type         varchar(32)           not null
        constraint chk_ai_mrlog_source
            check ((source_type)::text = ANY
                   ((ARRAY ['consultation'::character varying, 'dictation'::character varying, 'exam'::character varying])::text[])),
    ai_readme           text,
    ai_present          text,
    ai_history          text,
    ai_allergy          text,
    ai_physique         text,
    ai_diagnosis        text,
    is_adopted          smallint    default 0 not null
        constraint chk_ai_mrlog_adopted
            check (is_adopted = ANY (ARRAY [0, 1, 2])),
    doctor_modification text,
    generation_time     timestamp   default CURRENT_TIMESTAMP,
    model_id            varchar(64) default NULL::character varying
);

comment on table ai_medical_record_log is 'AI病历生成日志表';

comment on column ai_medical_record_log.doctor_modification is '医生修改内容，JSON格式记录被修改的字段和修改前后值';

alter table ai_medical_record_log
    owner to postgres;

create index idx_ai_mrlog_register_id
    on ai_medical_record_log (register_id);

