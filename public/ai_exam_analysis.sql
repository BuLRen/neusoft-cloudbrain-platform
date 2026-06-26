create table ai_exam_analysis
(
    id                    serial
        primary key,
    register_id           integer                                         not null
        constraint fk_ai_exam_analysis_register
            references register,
    check_request_id      integer
        constraint fk_ai_exam_analysis_check
            references check_request
            on delete set null,
    inspection_request_id integer
        constraint fk_ai_exam_analysis_inspection
            references inspection_request
            on delete set null,
    analysis_type         varchar(16)                                     not null
        constraint chk_ai_exam_analysis_type
            check ((analysis_type)::text = ANY
                   ((ARRAY ['check'::character varying, 'inspection'::character varying])::text[])),
    original_result       text,
    abnormal_indicators   text,
    risk_level            varchar(16) default 'normal'::character varying not null
        constraint chk_ai_exam_analysis_risk
            check ((risk_level)::text = ANY
                   ((ARRAY ['normal'::character varying, 'attention'::character varying, 'warning'::character varying, 'danger'::character varying])::text[])),
    analysis_report       text,
    correlation_analysis  text,
    is_viewed             smallint    default 0                           not null
        constraint chk_ai_exam_analysis_viewed
            check (is_viewed = ANY (ARRAY [0, 1])),
    analysis_time         timestamp   default CURRENT_TIMESTAMP,
    model_id              varchar(64) default NULL::character varying
);

comment on table ai_exam_analysis is 'AI检查/检验结果分析表';

comment on column ai_exam_analysis.abnormal_indicators is '异常指标，JSON格式，如: [{"指标":"白细胞","值":"15.2","参考值":"4-10","单位":"10^9/L"}]';

alter table ai_exam_analysis
    owner to postgres;

create index idx_ai_exam_analysis_register_id
    on ai_exam_analysis (register_id);

create index idx_ai_exam_analysis_check_id
    on ai_exam_analysis (check_request_id);

create index idx_ai_exam_analysis_inspection_id
    on ai_exam_analysis (inspection_request_id);

create index idx_ai_exam_analysis_risk
    on ai_exam_analysis (risk_level);

