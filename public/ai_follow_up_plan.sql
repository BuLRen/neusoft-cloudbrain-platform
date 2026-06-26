create table ai_follow_up_plan
(
    id               serial
        primary key,
    register_id      integer                                          not null
        constraint fk_ai_followup_register
            references register,
    prescription_id  integer
        constraint fk_ai_followup_prescription
            references prescription
            on delete set null,
    follow_up_day    integer,
    planned_date     date,
    follow_up_type   varchar(32)                                      not null
        constraint chk_ai_followup_type
            check ((follow_up_type)::text = ANY
                   ((ARRAY ['medication'::character varying, 'side_effect'::character varying, 'recovery'::character varying, 'revisit'::character varying])::text[])),
    content_template text,
    plan_status      varchar(16) default 'pending'::character varying not null
        constraint chk_ai_followup_status
            check ((plan_status)::text = ANY
                   ((ARRAY ['pending'::character varying, 'completed'::character varying, 'overdue'::character varying, 'cancelled'::character varying])::text[])),
    creation_time    timestamp   default CURRENT_TIMESTAMP,
    model_id         varchar(64) default NULL::character varying
);

comment on table ai_follow_up_plan is 'AI用药随访计划表';

comment on column ai_follow_up_plan.follow_up_type is '随访类型: medication-用药跟踪, side_effect-副作用反馈, recovery-康复评估, revisit-复诊提醒';

alter table ai_follow_up_plan
    owner to postgres;

create index idx_ai_followup_register_id
    on ai_follow_up_plan (register_id);

create index idx_ai_followup_status
    on ai_follow_up_plan (plan_status);

create index idx_ai_followup_planned_date
    on ai_follow_up_plan (planned_date);

