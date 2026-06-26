create table follow_up_interview_schedule
(
    id                 serial
        primary key,
    register_id        integer                  not null
        constraint fk_followup_interview_register
            references register,
    case_number        varchar(64),
    patient_name       varchar(64),
    week_start_date    date                     not null,
    status             varchar(16) default 'scheduled'::character varying not null
        constraint chk_followup_interview_status
            check ((status)::text = ANY
                   ((ARRAY ['scheduled'::character varying, 'completed'::character varying, 'cancelled'::character varying])::text[])),
    trigger_reason     text,
    trigger_metric_key varchar(64),
    created_by         integer,
    patient_notified   smallint    default 0,
    creation_time      timestamp   default CURRENT_TIMESTAMP,
    constraint uk_followup_interview_week unique (register_id, week_start_date)
);

comment on table follow_up_interview_schedule is '随访每周患者访谈日程表';

create index idx_followup_interview_week
    on follow_up_interview_schedule (week_start_date);

alter table follow_up_interview_schedule
    owner to postgres;
