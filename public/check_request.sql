create table check_request
(
    id                     serial
        primary key,
    register_id            integer                                          not null
        constraint fk_check_request_register
            references register,
    medical_technology_id  integer                                          not null
        constraint fk_check_request_medtech
            references medical_technology,
    check_info             varchar(512) default NULL::character varying,
    check_position         varchar(255) default NULL::character varying,
    creation_time          timestamp    default CURRENT_TIMESTAMP,
    check_employee_id      integer
        constraint fk_check_request_employee
            references employee
            on delete set null,
    inputcheck_employee_id integer
        constraint fk_check_request_input_employee
            references employee
            on delete set null,
    check_time             timestamp,
    check_result           varchar(512) default NULL::character varying,
    check_state            varchar(64)  default '待检查'::character varying not null
        constraint chk_check_request_state
            check ((check_state)::text = ANY
                   ((ARRAY ['待检查'::character varying, '检查中'::character varying, '已完成'::character varying])::text[])),
    check_remark           varchar(512) default NULL::character varying
);

comment on table check_request is '检查申请表';

comment on column check_request.check_state is '状态: 待检查 → 检查中 → 已完成';

alter table check_request
    owner to postgres;

create index idx_check_request_register_id
    on check_request (register_id);

create index idx_check_request_state
    on check_request (check_state);

create index idx_check_request_medtech_id
    on check_request (medical_technology_id);

