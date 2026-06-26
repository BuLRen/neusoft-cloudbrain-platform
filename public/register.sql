create table register
(
    id                 serial
        primary key,
    case_number        varchar(64)             not null,
    real_name          varchar(64)             not null,
    gender             varchar(6)    default NULL::character varying
        constraint chk_register_gender
            check ((gender IS NULL) OR
                   ((gender)::text = ANY ((ARRAY ['男'::character varying, '女'::character varying])::text[]))),
    card_number        varchar(18)   default NULL::character varying,
    birthdate          date,
    age                integer,
    age_type           varchar(6)    default NULL::character varying,
    home_address       varchar(128)  default NULL::character varying,
    visit_date         timestamp,
    noon               varchar(6)    default NULL::character varying
        constraint chk_register_noon
            check ((noon IS NULL) OR
                   ((noon)::text = ANY ((ARRAY ['上午'::character varying, '下午'::character varying])::text[]))),
    deptment_id        integer                 not null
        constraint fk_register_department
            references department,
    employee_id        integer                 not null
        constraint fk_register_employee
            references employee,
    regist_level_id    integer                 not null
        constraint fk_register_regist_level
            references regist_level,
    settle_category_id integer
        constraint fk_register_settle_category
            references settle_category
            on delete set null,
    is_book            varchar(2)    default '否'::character varying
        constraint chk_register_is_book
            check ((is_book)::text = ANY ((ARRAY ['是'::character varying, '否'::character varying])::text[])),
    regist_method      varchar(10)   default NULL::character varying,
    regist_money       numeric(8, 2) default 0.00
        constraint chk_register_regist_money
            check (regist_money >= (0)::numeric),
    visit_state        smallint      default 1 not null
        constraint chk_register_visit_state
            check (visit_state = ANY (ARRAY [1, 2, 3, 4]))
);

comment on table register is '患者历次挂号信息表';

comment on column register.case_number is '病历号，同一患者多次挂号共用同一病历号';

comment on column register.visit_state is '看诊状态: 1-已挂号, 2-医生接诊, 3-看诊结束, 4-已退号';

alter table register
    owner to postgres;

create index idx_register_case_number
    on register (case_number);

create index idx_register_real_name
    on register (real_name);

create index idx_register_visit_state
    on register (visit_state);

create index idx_register_employee_id
    on register (employee_id);

create index idx_register_deptment_id
    on register (deptment_id);

create index idx_register_visit_date
    on register (visit_date);

