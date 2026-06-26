create table ai_follow_up_record
(
    id                serial
        primary key,
    follow_up_plan_id integer not null
        constraint fk_ai_followup_record_plan
            references ai_follow_up_plan,
    register_id       integer not null
        constraint fk_ai_followup_record_register
            references register,
    is_on_time        smallint
        constraint chk_ai_fur_ontime
            check ((is_on_time IS NULL) OR (is_on_time = ANY (ARRAY [0, 1]))),
    side_effect       text,
    has_side_effect   smallint
        constraint chk_ai_fur_side_effect
            check ((has_side_effect IS NULL) OR (has_side_effect = ANY (ARRAY [0, 1]))),
    symptom_relief    varchar(32) default NULL::character varying
        constraint chk_ai_fur_relief
            check ((symptom_relief IS NULL) OR ((symptom_relief)::text = ANY
                                                ((ARRAY ['relieved'::character varying, 'partial'::character varying, 'unchanged'::character varying, 'worsened'::character varying])::text[]))),
    need_revisit      smallint
        constraint chk_ai_fur_revisit
            check ((need_revisit IS NULL) OR (need_revisit = ANY (ARRAY [0, 1]))),
    patient_feedback  text,
    ai_assessment     text,
    ai_advice         text,
    follow_up_time    timestamp,
    model_id          varchar(64) default NULL::character varying
);

comment on table ai_follow_up_record is 'AI随访反馈记录表';

alter table ai_follow_up_record
    owner to postgres;

create index idx_ai_fur_plan_id
    on ai_follow_up_record (follow_up_plan_id);

create index idx_ai_fur_register_id
    on ai_follow_up_record (register_id);

