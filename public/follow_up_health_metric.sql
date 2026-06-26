create table follow_up_health_metric
(
    id           serial
        primary key,
    register_id  integer                  not null
        constraint fk_followup_metric_register
            references register,
    record_date  date                     not null,
    metric_key   varchar(64)              not null,
    metric_value numeric(10, 2)           not null,
    unit         varchar(16),
    source       varchar(16) default 'simulated'::character varying not null,
    note         text,
    constraint uk_followup_metric unique (register_id, record_date, metric_key)
);

comment on table follow_up_health_metric is '随访疗效评估模拟健康指标表（EAV）';

create index idx_followup_metric_register_date
    on follow_up_health_metric (register_id, record_date);

alter table follow_up_health_metric
    owner to postgres;
