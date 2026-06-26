create table department
(
    id        serial
        primary key,
    dept_code varchar(64)           not null
        constraint uk_department_dept_code
            unique,
    dept_name varchar(64)           not null,
    dept_type varchar(64) default NULL::character varying,
    delmark   smallint    default 1 not null
        constraint chk_department_delmark
            check (delmark = ANY (ARRAY [0, 1]))
);

comment on table department is '科室表';

comment on column department.id is '主键ID';

comment on column department.dept_code is '科室编码，如: SJNK(神经内科)';

comment on column department.dept_name is '科室名称';

comment on column department.dept_type is '科室类型: 临床科室、医技科室等';

comment on column department.delmark is '生效标记: 1-正常, 0-已删除';

alter table department
    owner to postgres;

