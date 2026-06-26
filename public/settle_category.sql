create table settle_category
(
    id          serial
        primary key,
    settle_code varchar(64)        not null
        constraint uk_settle_category_code
            unique,
    settle_name varchar(64)        not null,
    sequence_no integer  default 0,
    delmark     smallint default 1 not null
        constraint chk_settle_category_delmark
            check (delmark = ANY (ARRAY [0, 1]))
);

comment on table settle_category is '结算类别表';

alter table settle_category
    owner to postgres;

