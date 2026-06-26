create table disposal_request
(
    id                        serial
        primary key,
    register_id               integer                                          not null
        constraint fk_disposal_request_register
            references register,
    medical_technology_id     integer                                          not null
        constraint fk_disposal_request_medtech
            references medical_technology,
    disposal_info             varchar(512) default NULL::character varying,
    disposal_position         varchar(255) default NULL::character varying,
    creation_time             timestamp    default CURRENT_TIMESTAMP,
    disposal_employee_id      integer
        constraint fk_disposal_request_employee
            references employee
            on delete set null,
    inputdisposal_employee_id integer
        constraint fk_disposal_request_input_employee
            references employee
            on delete set null,
    disposal_time             timestamp,
    disposal_result           varchar(512) default NULL::character varying,
    disposal_state            varchar(64)  default '待处置'::character varying not null
        constraint chk_disposal_state
            check ((disposal_state)::text = ANY
                   ((ARRAY ['待处置'::character varying, '处置中'::character varying, '已完成'::character varying])::text[])),
    disposal_remark           varchar(512) default NULL::character varying
);

comment on table disposal_request is '处置申请表';

alter table disposal_request
    owner to postgres;

create index idx_disposal_request_register_id
    on disposal_request (register_id);

create index idx_disposal_request_state
    on disposal_request (disposal_state);

