create table prescription
(
    id            serial
        primary key,
    register_id   integer                                        not null
        constraint fk_prescription_register
            references register,
    drug_id       integer                                        not null
        constraint fk_prescription_drug
            references drug_info,
    drug_usage    varchar(255) default NULL::character varying,
    drug_number   varchar(255) default NULL::character varying,
    creation_time timestamp    default CURRENT_TIMESTAMP,
    drug_state    varchar(64)  default '未发'::character varying not null
        constraint chk_prescription_state
            check ((drug_state)::text = ANY
                   ((ARRAY ['未发'::character varying, '已发'::character varying, '已退'::character varying])::text[]))
);

comment on table prescription is '处方表（每条记录对应一个药品）';

comment on column prescription.drug_state is '状态: 未发 → 已发，或 未发 → 已退';

alter table prescription
    owner to postgres;

create index idx_prescription_register_id
    on prescription (register_id);

create index idx_prescription_drug_id
    on prescription (drug_id);

create index idx_prescription_state
    on prescription (drug_state);

