create table scheduling
(
    id        serial
        primary key,
    rule_name varchar(64)           not null,
    week_rule varchar(14) default NULL::character varying,
    delmark   smallint    default 1 not null
        constraint chk_scheduling_delmark
            check (delmark = ANY (ARRAY [0, 1]))
);

comment on table scheduling is '排班规则表';

comment on column scheduling.week_rule is '星期规则，格式: 数字+午别，如 1上3上5上 表示周一三五上午';

alter table scheduling
    owner to postgres;

