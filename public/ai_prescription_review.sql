create table ai_prescription_review
(
    id              serial
        primary key,
    register_id     integer               not null
        constraint fk_ai_review_register
            references register,
    prescription_id integer               not null
        constraint fk_ai_review_prescription
            references prescription,
    review_result   varchar(16)           not null
        constraint chk_ai_review_result
            check ((review_result)::text = ANY
                   ((ARRAY ['passed'::character varying, 'warning'::character varying, 'rejected'::character varying])::text[])),
    drug_conflict   text,
    allergy_risk    text,
    duplicate_drug  text,
    dosage_check    text,
    risk_score      integer     default 0 not null
        constraint chk_ai_review_score
            check ((risk_score >= 0) AND (risk_score <= 100)),
    risk_details    text,
    doctor_action   varchar(16) default NULL::character varying
        constraint chk_ai_review_action
            check ((doctor_action IS NULL) OR ((doctor_action)::text = ANY
                                               ((ARRAY ['accepted'::character varying, 'overridden'::character varying])::text[]))),
    review_time     timestamp   default CURRENT_TIMESTAMP,
    model_id        varchar(64) default NULL::character varying
);

comment on table ai_prescription_review is 'AI处方审核表';

comment on column ai_prescription_review.risk_score is '综合风险评分0-100，0无风险，100极高风险';

alter table ai_prescription_review
    owner to postgres;

create index idx_ai_review_register_id
    on ai_prescription_review (register_id);

create index idx_ai_review_prescription_id
    on ai_prescription_review (prescription_id);

create index idx_ai_review_result
    on ai_prescription_review (review_result);

