create table regist_level
(
    id           serial
        primary key,
    regist_code  varchar(64)                not null
        constraint uk_regist_level_code
            unique,
    regist_name  varchar(64)                not null,
    regist_fee   numeric(8, 2) default 0.00 not null
        constraint chk_regist_level_fee
            check (regist_fee >= (0)::numeric),
    regist_quota integer       default 0
        constraint chk_regist_level_quota
            check (regist_quota >= 0),
    sequence_no  integer       default 0,
    delmark      smallint      default 1    not null
        constraint chk_regist_level_delmark
            check (delmark = ANY (ARRAY [0, 1]))
);

comment on table regist_level is '挂号级别表';

comment on column regist_level.id is '主键ID';

comment on column regist_level.regist_code is '号别编码';

comment on column regist_level.regist_name is '号别名称';

comment on column regist_level.regist_fee is '挂号费（元）';

comment on column regist_level.regist_quota is '每半天挂号限额';

comment on column regist_level.sequence_no is '显示顺序号';

comment on column regist_level.delmark is '生效标记: 1-正常, 0-已删除';

alter table regist_level
    owner to postgres;

