package com.xikang.physician.ai;

import com.xikang.physician.mapper.PhysicianMapper;
import com.xikang.physician.service.PhysicianService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds {@code clinical_context_json} for the Dify W2 examination-recommendation workflow.
 */
@Component
public class W2ClinicalContextBuilder {

    private final PhysicianMapper physicianMapper;
    private final PhysicianService physicianService;

    public W2ClinicalContextBuilder(PhysicianMapper physicianMapper, PhysicianService physicianService) {
        this.physicianMapper = physicianMapper;
        this.physicianService = physicianService;
    }

    public Map<String, Object> build(Long registerId) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("registerId", registerId);

        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (register != null) {
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("realName", register.get("realName"));
            patient.put("gender", register.get("gender"));
            patient.put("age", register.get("age"));
            patient.put("caseNumber", register.get("caseNumber"));
            ctx.put("patient", patient);
        }

        Map<String, Object> recordDto = physicianService.getMedicalRecord(registerId);
        ctx.put("record", buildRecordSection(recordDto));
        ctx.put("diagnosis", buildDiagnosisSection(recordDto));
        ctx.put("preConsultation", buildPreConsultationSection(registerId));
        ctx.put("meta", buildMeta(recordDto, ctx));

        return ctx;
    }

    private static Map<String, Object> buildRecordSection(Map<String, Object> recordDto) {
        Map<String, Object> record = new LinkedHashMap<>();
        if (recordDto == null) {
            return record;
        }
        putIfPresent(record, "chiefComplaint", recordDto.get("readme"));
        putIfPresent(record, "presentIllness", recordDto.get("present"));
        putIfPresent(record, "presentTreat", recordDto.get("presentTreat"));
        putIfPresent(record, "history", recordDto.get("history"));
        putIfPresent(record, "allergy", recordDto.get("allergy"));
        putIfPresent(record, "physique", recordDto.get("physique"));
        putIfPresent(record, "examProposal", recordDto.get("proposal"));
        return record;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildDiagnosisSection(Map<String, Object> recordDto) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        if (recordDto == null) {
            return diagnosis;
        }
        putIfPresent(diagnosis, "doctorConfirmed", recordDto.get("preliminaryDiagnosis"));

        Object metaRaw = recordDto.get("preliminaryAiMeta");
        if (!(metaRaw instanceof Map<?, ?> meta) || meta.isEmpty()) {
            return diagnosis;
        }

        copyIfPresent(meta, diagnosis, "clinicalSummary");
        copyIfPresent(meta, diagnosis, "primaryDiagnosis");
        copyIfPresent(meta, diagnosis, "diagnosisBasis");
        copyIfPresent(meta, diagnosis, "redFlags");
        copyIfPresent(meta, diagnosis, "excludedDiagnoses");

        List<String> workup = collectSuggestedWorkup((Map<String, Object>) meta);
        if (!workup.isEmpty()) {
            diagnosis.put("suggestedWorkupFromAi", workup);
        }

        Object diseases = meta.get("suggestedDiseases");
        if (diseases instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> differential = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                copyIfPresent(row, entry, "diseaseName");
                copyFirstPresent(row, entry, "reason", "rationale", "diagnosisBasis", "symptoms");
                if (row.get("role") != null) {
                    entry.put("role", row.get("role"));
                } else {
                    entry.put("role", "differential");
                }
                if (!entry.isEmpty()) {
                    differential.add(entry);
                }
            }
            if (!differential.isEmpty()) {
                diagnosis.put("differential", differential);
            }
        }

        return diagnosis;
    }

    @SuppressWarnings("unchecked")
    private static List<String> collectSuggestedWorkup(Map<String, Object> meta) {
        Set<String> names = new LinkedHashSet<>();
        Object diseases = meta.get("suggestedDiseases");
        if (diseases instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                Object workup = row.get("recommendedWorkup");
                if (workup == null) {
                    workup = row.get("recommended_workup");
                }
                if (workup instanceof List<?> workupList) {
                    for (Object w : workupList) {
                        if (w != null) {
                            String text = String.valueOf(w).trim();
                            if (!text.isEmpty()) {
                                names.add(text);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    private Map<String, Object> buildPreConsultationSection(Long registerId) {
        Map<String, Object> consult = physicianMapper.selectLatestAiConsultation(registerId);
        if (consult == null || consult.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> pre = new LinkedHashMap<>();
        putIfPresent(pre, "chiefComplaint", consult.get("chiefComplaint"));
        putIfPresent(pre, "aiSummary", consult.get("aiSummary"));
        putIfPresent(pre, "suggestedExam", consult.get("suggestedExam"));
        return pre;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildMeta(Map<String, Object> recordDto, Map<String, Object> ctx) {
        Map<String, Object> meta = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        Map<String, Object> record = ctx.get("record") instanceof Map<?, ?> r
            ? (Map<String, Object>) r
            : Map.of();
        Map<String, Object> diagnosis = ctx.get("diagnosis") instanceof Map<?, ?> d
            ? (Map<String, Object>) d
            : Map.of();

        if (isBlank(record.get("chiefComplaint"))) {
            missing.add("chiefComplaint");
        }
        if (isBlank(record.get("examProposal"))) {
            missing.add("examProposal");
        }
        if (isBlank(diagnosis.get("doctorConfirmed"))) {
            missing.add("doctorConfirmed");
        }
        if (recordDto == null) {
            missing.add("medicalRecord");
        }

        String completeness;
        if (missing.isEmpty()) {
            completeness = "full";
        } else if (!isBlank(record.get("chiefComplaint"))) {
            completeness = "partial";
        } else {
            completeness = "minimal";
        }

        meta.put("dataCompleteness", completeness);
        meta.put("missingFields", missing);
        return meta;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            target.put(key, text);
        }
    }

    private static void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && (!(value instanceof String) || !((String) value).isBlank())) {
            target.put(key, value);
        }
    }

    private static void copyFirstPresent(Map<?, ?> source, Map<String, Object> target, String targetKey, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && (!(value instanceof String) || !((String) value).isBlank())) {
                target.put(targetKey, value);
                return;
            }
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }
}
