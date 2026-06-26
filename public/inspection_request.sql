create table inspection_request
(
    id                          serial
        primary key,
    register_id                 integer                                          not null
        constraint fk_inspection_request_register
            references register,
    medical_technology_id       integer                                          not null
        constraint fk_inspection_request_medtech
            references medical_technology,
    inspection_info             varchar(512) default NULL::character varying,
    inspection_position         varchar(255) default NULL::character varying,
    creation_time               timestamp    default CURRENT_TIMESTAMP,
    inspection_employee_id      integer
        constraint fk_inspection_request_employee
            references employee
            on delete set null,
    inputinspection_employee_id integer
        constraint fk_inspection_request_input_employee
            references employee
            on delete set null,
    inspection_time             timestamp,
    inspection_result           varchar(512) default NULL::character varying,
    inspection_state            varchar(64)  default '待检验'::character varying not null
        constraint chk_inspection_state
            check ((inspection_state)::text = ANY
                   ((ARRAY ['待检验'::character varying, '检验中'::character varying, '已完成'::character varying])::text[])),
    inspection_remark           varchar(512) default NULL::character varying
);

comment on table inspection_request is '检验申请表';

alter table inspection_request
    owner to postgres;

create index idx_inspection_request_register_id
    on inspection_request (register_id);

create index idx_inspection_request_state
    on inspection_request (inspection_state);

