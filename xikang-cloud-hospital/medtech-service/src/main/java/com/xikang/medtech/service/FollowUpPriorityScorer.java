package com.xikang.medtech.service;

import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.dto.FollowUpPriorityScorerContext;
import com.xikang.medtech.mapper.FollowUpClinicalMapper;
import com.xikang.medtech.mapper.FollowUpLastVisitMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FollowUpPriorityScorer {

    public static final int ONCOLOGY_DEPARTMENT_ID = 18;

    private static final String[] CRITICAL_DIAGNOSIS_KEYWORDS = {
        "癌", "恶性肿瘤", "危急", "危重", "急性心梗", "急性心肌梗死", "脑卒中", "休克"
    };
    private static final String[] CHRONIC_DEPT_KEYWORDS = {
        "内分泌", "心血管", "呼吸", "肾内", "风湿", "血液"
    };
    private static final String[] CHRONIC_DISEASE_KEYWORDS = {
        "糖尿病", "高血压", "慢阻肺", "COPD", "慢性肾", "冠心病", "心衰", "哮喘", "痛风"
    };

    private final FollowUpClinicalMapper clinicalMapper;
    private final FollowUpOutcomeMapper outcomeMapper;
    private final FollowUpLastVisitMapper lastVisitMapper;

    public FollowUpPriorityScorer(
        FollowUpClinicalMapper clinicalMapper,
        FollowUpOutcomeMapper outcomeMapper,
        FollowUpLastVisitMapper lastVisitMapper
    ) {
        this.clinicalMapper = clinicalMapper;
        this.outcomeMapper = outcomeMapper;
        this.lastVisitMapper = lastVisitMapper;
    }

    public FollowUpPriorityResult score(Long registerId) {
        return score(buildContext(registerId));
    }

    public FollowUpPriorityScorerContext buildContext(Long registerId) {
        FollowUpPriorityScorerContext ctx = new FollowUpPriorityScorerContext();

        Map<String, Object> dept = clinicalMapper.selectRegisterDepartmentBrief(registerId);
        if (dept != null) {
            ctx.setDepartmentId(toInt(dept.get("departmentId")));
            ctx.setDepartmentName(text(dept.get("departmentName")));
        }

        Map<String, Object> medicalRecord = clinicalMapper.selectMedicalRecordByRegisterId(registerId);
        if (medicalRecord != null && medicalRecord.get("diagnosis") != null) {
            ctx.setDiagnosisText(String.valueOf(medicalRecord.get("diagnosis")));
        }

        List<String> diseaseNames = new ArrayList<>();
        for (Map<String, Object> disease : outcomeMapper.selectPatientDiseases(registerId)) {
            if (disease.get("diseaseName") != null) {
                diseaseNames.add(String.valueOf(disease.get("diseaseName")));
            }
        }
        ctx.setDiseaseNames(diseaseNames);

        Set<Object> drugIds = new HashSet<>();
        for (Map<String, Object> rx : clinicalMapper.selectPrescriptionsByRegisterId(registerId)) {
            Object drugId = rx.get("drugId") != null ? rx.get("drugId") : rx.get("drugName");
            if (drugId != null) {
                drugIds.add(drugId);
            }
        }
        ctx.setDistinctDrugCount(drugIds.size());

        int high = 0;
        int any = 0;
        for (Map<String, Object> lab : lastVisitMapper.selectLabItemsByRegisterId(registerId)) {
            String flag = normalizeFlag(lab.get("abnormalFlag") != null ? lab.get("abnormalFlag") : lab.get("abnormal_flag"));
            if (flag.isEmpty()) {
                continue;
            }
            any++;
            if ("high".equals(flag) || "critical".equals(flag) || "h".equals(flag)) {
                high++;
            }
        }
        ctx.setHighAbnormalCount(high);
        ctx.setAnyAbnormalCount(any);
        return ctx;
    }

    public FollowUpPriorityResult score(FollowUpPriorityScorerContext ctx) {
        if (ctx == null) {
            return FollowUpPriorityResult.of("normal", List.of("default"));
        }

        List<String> criticalRules = new ArrayList<>();
        if (ctx.getDepartmentId() != null && ctx.getDepartmentId() == ONCOLOGY_DEPARTMENT_ID) {
            criticalRules.add("oncology_department");
        }
        if (containsKeyword(ctx.getDepartmentName(), "肿瘤")) {
            criticalRules.add("oncology_department_name");
        }
        if (containsAnyKeyword(combinedDiagnosis(ctx), CRITICAL_DIAGNOSIS_KEYWORDS)) {
            criticalRules.add("critical_diagnosis_text");
        }
        if (containsAnyKeyword(combinedDiseases(ctx), new String[] { "癌", "恶性肿瘤" })) {
            criticalRules.add("malignancy_disease");
        }
        if (ctx.getHighAbnormalCount() >= 3) {
            criticalRules.add("lab_high_abnormal_ge_3");
        }
        if (!criticalRules.isEmpty()) {
            return FollowUpPriorityResult.of("critical", criticalRules);
        }

        List<String> highRules = new ArrayList<>();
        if (isChronicDepartment(ctx.getDepartmentName()) && containsAnyKeyword(combinedDiagnosis(ctx), CHRONIC_DISEASE_KEYWORDS)) {
            highRules.add("chronic_department_and_diagnosis");
        }
        if (ctx.getDistinctDrugCount() >= 3) {
            highRules.add("prescription_ge_3_drugs");
        }
        if (ctx.getAnyAbnormalCount() >= 1) {
            highRules.add("lab_abnormal_ge_1");
        }
        if (!highRules.isEmpty()) {
            return FollowUpPriorityResult.of("high", highRules);
        }

        return FollowUpPriorityResult.of("normal", List.of("default"));
    }

    private static String combinedDiagnosis(FollowUpPriorityScorerContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.getDiagnosisText() != null) {
            sb.append(ctx.getDiagnosisText());
        }
        for (String name : ctx.getDiseaseNames()) {
            sb.append(' ').append(name);
        }
        return sb.toString();
    }

    private static String combinedDiseases(FollowUpPriorityScorerContext ctx) {
        return String.join(" ", ctx.getDiseaseNames());
    }

    private static boolean isChronicDepartment(String departmentName) {
        return containsAnyKeyword(departmentName, CHRONIC_DEPT_KEYWORDS);
    }

    private static boolean containsAnyKeyword(String text, String[] keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsKeyword(String text, String keyword) {
        return text != null && text.contains(keyword);
    }

    private static String normalizeFlag(Object flag) {
        if (flag == null) {
            return "";
        }
        return String.valueOf(flag).trim().toLowerCase(Locale.ROOT);
    }

    private static Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
