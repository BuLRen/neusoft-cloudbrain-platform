create table ai_exam_suggestion
(
    id             serial
        primary key,
    register_id    integer               not null
        constraint fk_ai_exam_sug_register
            references register,
    tech_id        integer               not null
        constraint fk_ai_exam_sug_medtech
            references medical_technology,
    tech_name      varchar(64) default NULL::character varying,
    suggest_type   varchar(16)           not null
        constraint chk_ai_exam_sug_type
            check ((suggest_type)::text = ANY
                   ((ARRAY ['check'::character varying, 'inspection'::character varying])::text[])),
    suggest_reason text,
    priority       integer     default 1 not null
        constraint chk_ai_exam_sug_priority
            check (priority = ANY (ARRAY [1, 2, 3])),
    is_adopted     smallint    default 0 not null
        constraint chk_ai_exam_sug_adopted
            check (is_adopted = ANY (ARRAY [0, 1])),
    creation_time  timestamp   default CURRENT_TIMESTAMP,
    model_id       varchar(64) default NULL::character varying
);

comment on table ai_exam_suggestion is 'AI检查/检验推荐表';

alter table ai_exam_suggestion
    owner to postgres;

create index idx_ai_exam_sug_register_id
    on ai_exam_suggestion (register_id);

