create table disease
(
    id               serial
        primary key,
    disease_code     varchar(64) default NULL::character varying,
    disease_name     varchar(255) not null,
    diseaseicd       varchar(64) default NULL::character varying
        constraint uk_disease_icd
            unique,
    disease_category varchar(64) default NULL::character varying
);

comment on table disease is '疾病字典表';

comment on column disease.diseaseicd is '国际ICD编码，如: G43.9(偏头痛)';

alter table disease
    owner to postgres;

create index idx_disease_name
    on disease (disease_name);

create index idx_disease_category
    on disease (disease_category);

