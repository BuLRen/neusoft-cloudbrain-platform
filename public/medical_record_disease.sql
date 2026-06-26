create table medical_record_disease
(
    medical_record_id integer not null
        constraint fk_mrd_medical_record
            references medical_record
            on delete cascade,
    disease_id        integer not null
        constraint fk_mrd_disease
            references disease,
    primary key (medical_record_id, disease_id)
);

comment on table medical_record_disease is '病历-疾病关联表（多对多）';

alter table medical_record_disease
    owner to postgres;

