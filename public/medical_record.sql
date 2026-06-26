create table medical_record
(
    id            serial
        primary key,
    register_id   integer not null
        constraint uk_medical_record_register
            unique
        constraint fk_medical_record_register
            references register,
    readme        varchar(512) default NULL::character varying,
    present       varchar(512) default NULL::character varying,
    present_treat varchar(512) default NULL::character varying,
    history       varchar(512) default NULL::character varying,
    allergy       varchar(512) default NULL::character varying,
    physique      varchar(512) default NULL::character varying,
    proposal      varchar(512) default NULL::character varying,
    careful       varchar(512) default NULL::character varying,
    diagnosis     varchar(512) default NULL::character varying,
    cure          varchar(512) default NULL::character varying
);

comment on table medical_record is '患者病历表';

alter table medical_record
    owner to postgres;

