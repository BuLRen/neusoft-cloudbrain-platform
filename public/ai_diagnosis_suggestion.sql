create table ai_diagnosis_suggestion
(
    id                  serial
        primary key,
    register_id         integer                                        not null
        constraint fk_ai_diagnosis_register
            references register,
    disease_id          integer
        constraint fk_ai_diagnosis_disease
            references disease
            on delete set null,
    disease_name        varchar(255)  default NULL::character varying,
    recommend_icd       varchar(64)   default NULL::character varying,
    probability         numeric(5, 2) default NULL::numeric
        constraint chk_ai_diagnosis_probability
            check ((probability IS NULL) OR ((probability >= (0)::numeric) AND (probability <= (100)::numeric))),
    risk_level          varchar(16)   default 'low'::character varying not null
        constraint chk_ai_diagnosis_risk
            check ((risk_level)::text = ANY
                   ((ARRAY ['low'::character varying, 'medium'::character varying, 'high'::character varying])::text[])),
    treatment_direction text,
    diagnosis_basis     text,
    is_adopted          smallint      default 0                        not null
        constraint chk_ai_diagnosis_adopted
            check (is_adopted = ANY (ARRAY [0, 1])),
    sort_order          integer       default 1                        not null,
    creation_time       timestamp     default CURRENT_TIMESTAMP,
    model_id            varchar(64)   default NULL::character varying
);

comment on table ai_diagnosis_suggestion is 'AI辅助诊断建议表';

comment on column ai_diagnosis_suggestion.probability is '疾病概率百分比，如85.50表示85.50%';

alter table ai_diagnosis_suggestion
    owner to postgres;

create index idx_ai_diagnosis_register_id
    on ai_diagnosis_suggestion (register_id);

create index idx_ai_diagnosis_disease_id
    on ai_diagnosis_suggestion (disease_id);

