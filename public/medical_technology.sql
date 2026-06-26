create table medical_technology
(
    id          serial
        primary key,
    tech_code   varchar(64)                not null
        constraint uk_medtech_code
            unique,
    tech_name   varchar(64)                not null,
    tech_format varchar(64)   default NULL::character varying,
    tech_price  numeric(8, 2) default 0.00 not null
        constraint chk_medtech_price
            check (tech_price >= (0)::numeric),
    tech_type   varchar(64)                not null
        constraint chk_medtech_type
            check ((tech_type)::text = ANY
                   ((ARRAY ['check'::character varying, 'inspection'::character varying, 'disposal'::character varying])::text[])),
    price_type  varchar(64)   default NULL::character varying,
    deptment_id integer
        constraint fk_medtech_department
            references department
            on delete set null
);

comment on table medical_technology is '医技项目表（检查/检验/处置统一目录）';

comment on column medical_technology.tech_type is '项目类型: check-检查, inspection-检验, disposal-处置';

alter table medical_technology
    owner to postgres;

create index idx_medtech_type
    on medical_technology (tech_type);

create index idx_medtech_deptment_id
    on medical_technology (deptment_id);

