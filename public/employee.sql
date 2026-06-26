create table employee
(
    id              serial
        primary key,
    deptment_id     integer
        constraint fk_employee_department
            references department
            on delete set null,
    regist_level_id integer
        constraint fk_employee_regist_level
            references regist_level
            on delete set null,
    scheduling_id   integer
        constraint fk_employee_scheduling
            references scheduling
            on delete set null,
    realname        varchar(64)        not null,
    password        varchar(64)        not null,
    delmark         smallint default 1 not null
        constraint chk_employee_delmark
            check (delmark = ANY (ARRAY [0, 1]))
);

comment on table employee is '医院员工表';

comment on column employee.password is '密码（BCrypt加密存储，禁止明文）';

alter table employee
    owner to postgres;

create index idx_employee_deptment_id
    on employee (deptment_id);

create index idx_employee_regist_level_id
    on employee (regist_level_id);

