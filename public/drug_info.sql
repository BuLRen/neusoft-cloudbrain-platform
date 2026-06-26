create table drug_info
(
    id            serial
        primary key,
    drug_code     varchar(255)               not null
        constraint uk_drug_code
            unique,
    drug_name     varchar(255)               not null,
    drug_format   varchar(255)  default NULL::character varying,
    drug_unit     varchar(16)   default NULL::character varying,
    manufacturer  varchar(255)  default NULL::character varying,
    drug_dosage   varchar(64)   default NULL::character varying,
    drug_type     varchar(64)   default NULL::character varying,
    drug_price    numeric(8, 2) default 0.00 not null
        constraint chk_drug_price
            check (drug_price >= (0)::numeric),
    mnemonic_code varchar(255)  default NULL::character varying,
    creation_date date          default CURRENT_DATE
);

comment on table drug_info is '药品信息表';

comment on column drug_info.mnemonic_code is '拼音助记码，用于快速搜索';

alter table drug_info
    owner to postgres;

create index idx_drug_name
    on drug_info (drug_name);

create index idx_drug_mnemonic
    on drug_info (mnemonic_code);

create index idx_drug_type
    on drug_info (drug_type);

