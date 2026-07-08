package com.xikang.physician.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.common.agent.AgentToolExecutionContext;
import com.xikang.physician.client.CtViewerClient;
import com.xikang.physician.client.MedtechFollowUpClient;
import com.xikang.physician.client.NotificationClient;
import com.xikang.physician.client.PaymentClient;
import com.xikang.physician.context.PhysicianAuthContext;
import com.xikang.physician.mapper.PhysicianMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Physician Service
 */
@Service
public class PhysicianService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int VISIT_REGISTERED = 1;
    private static final int VISIT_IN_PROGRESS = 2;
    private static final int VISIT_ENDED = 3;
    private static final int VISIT_EXAM_PENDING = 5;
    private static final int VISIT_EXAM_COMPLETED = 6;

    private final PhysicianMapper physicianMapper;
    private final PaymentClient paymentClient;
    private final CtViewerClient ctViewerClient;
    private final MedtechFollowUpClient medtechFollowUpClient;
    private final NotificationClient notificationClient;

    public PhysicianService(
        PhysicianMapper physicianMapper,
        PaymentClient paymentClient,
        CtViewerClient ctViewerClient,
        MedtechFollowUpClient medtechFollowUpClient,
        NotificationClient notificationClient
    ) {
        this.physicianMapper = physicianMapper;
        this.paymentClient = paymentClient;
        this.ctViewerClient = ctViewerClient;
        this.medtechFollowUpClient = medtechFollowUpClient;
        this.notificationClient = notificationClient;
    }

    public Map<String, Object> getPatients(String keyword, Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : size;
        int offset = (currentPage - 1) * pageSize;
        Long employeeId = PhysicianAuthContext.employeeIdOrNull();
        List<Map<String, Object>> records = physicianMapper.selectPatients(keyword, employeeId, offset, pageSize).stream()
            .map(this::withAiConsultSummary)
            .map(this::syncExamStateIfNeeded)
            .toList();
        long total = physicianMapper.countPatients(keyword, employeeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", (long) Math.ceil(total / (double) pageSize));
        return result;
    }

    public Map<String, Object> getPatientStats() {
        return physicianMapper.selectPatientStats(PhysicianAuthContext.employeeIdOrNull());
    }

    public Map<String, Object> getHistoricalSummary() {
        return physicianMapper.selectHistoricalSummary(PhysicianAuthContext.employeeIdOrNull());
    }

    public Map<String, Object> getPatient(Long registerId) {
        assertRegisterAccess(registerId);
        Map<String, Object> row = physicianMapper.selectPatientByRegisterId(registerId);
        if (row == null) {
            return null;
        }
        return syncExamStateIfNeeded(withAiConsultSummary(row));
    }

    @Transactional
    public Map<String, Object> startEncounter(Long registerId) {
        assertRegisterAccess(registerId);
        int current = currentVisitState(registerId);
        if (current == VISIT_REGISTERED) {
            physicianMapper.updateVisitState(registerId, VISIT_IN_PROGRESS);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    @Transactional
    public Map<String, Object> endVisit(Long registerId) {
        assertRegisterAccess(registerId);
        paymentClient.assertAllPaid(registerId);
        int current = currentVisitState(registerId);
        if (current == VISIT_IN_PROGRESS || current == VISIT_EXAM_PENDING || current == VISIT_EXAM_COMPLETED) {
            physicianMapper.updateVisitState(registerId, VISIT_ENDED);
        }
        Map<String, Object> patient = physicianMapper.selectPatientByRegisterId(registerId);
        Long departmentId = patient != null && patient.get("departmentId") != null
            ? Long.valueOf(String.valueOf(patient.get("departmentId")))
            : null;
        medtechFollowUpClient.notifyVisitEnded(
            registerId,
            LocalDateTime.now(),
            PhysicianAuthContext.employeeIdOrNull(),
            departmentId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    public Map<String, Object> getMedicalRecord(Long registerId) {
        assertRegisterAccess(registerId);
        Map<String, Object> record = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        if (record == null) {
            return null;
        }
        Long medicalRecordId = toLong(record.get("id"));
        record.put("diseases", physicianMapper.selectDiseasesByMedicalRecordId(medicalRecordId));
        record.put("preliminaryAiMeta", loadPreliminaryAiMeta(registerId));
        return record;
    }

    @Transactional
    public void savePreliminaryDiagnosis(Map<String, Object> request) {
        Long registerId = toLong(request.get("registerId"));
        assertRegisterAccess(registerId);
        Map<String, Object> existing = physicianMapper.selectMedicalRecordByRegisterId(registerId);
        Long medicalRecordId;
        if (existing == null) {
            Map<String, Object> record = new HashMap<>();
            record.put("registerId", registerId);
            physicianMapper.insertMedicalRecord(record);
            medicalRecordId = toLong(record.get("id"));
        } else {
            medicalRecordId = toLong(existing.get("id"));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("id", medicalRecordId);
        update.put("preliminaryDiagnosis", request.get("preliminaryDiagnosis"));
        physicianMapper.updateMedicalRecordPreliminary(update);
        List<Long> ids = diseaseIds(request);
        if (!ids.isEmpty()) {
            syncRecordDiseases(medicalRecordId, ids);
        }
        persistPreliminaryDoctorSave(registerId, request);
    }

    private void persistPreliminaryDoctorSave(Long registerId, Map<String, Object> request) {
        List<String> names = stringList(request.get("suggestedDiseaseNames"));
        if (registerId == null || names.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("suggestedDiseaseNames", names);
            meta.put("savedBy", "doctor");
            Map<String, Object> log = new HashMap<>();
            log.put("registerId", registerId);
            log.put("sourceType", "preliminary_diagnosis");
            log.put("aiDiagnosis", request.get("preliminaryDiagnosis"));
            log.put("modelId", "doctor-save");
            log.put("doctorModification", JSON.writeValueAsString(meta));
            physicianMapper.insertAiMedicalRecordLog(log);
        } catch (Exception ignored) {
            // ignore log persistence errors
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    @Transactional
    public Map<String, Object> createMedicalRecord(Map<String, Object> request) {
        assertRegisterAccess(toLong(request.get("registerId")));
        Map<String, Object> record = copyRecordFields(request);
        physicianMapper.insertMedicalRecord(record);
        Long medicalRecordId = toLong(record.get("id"));
        syncRecordDiseases(medicalRecordId, diseaseIds(request));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", medicalRecordId);
        result.put("registerId", toLong(request.get("registerId")));
        return result;
    }

    @Transactional
    public void updateMedicalRecord(Long id, Map<String, Object> request) {
        Long registerId = physicianMapper.selectRegisterIdByMedicalRecordId(id);
        assertRegisterAccess(registerId);
        Map<String, Object> record = copyRecordFields(request);
        record.put("id", id);
        physicianMapper.updateMedicalRecord(record);
        syncRecordDiseases(id, diseaseIds(request));
    }

    public List<Map<String, Object>> getMedicalTechnologies(String techType, String keyword) {
        return physicianMapper.selectMedicalTechnologies(techType, keyword);
    }

    @Transactional
    public Map<String, Object> createCheckRequest(Map<String, Object> request) {
        assertRegisterAccess(toLong(request.get("registerId")));
        Map<String, Object> result = createTechnologyRequest(request, "check");
        markExamPending(toLong(request.get("registerId")));
        result.put("visitState", currentVisitState(toLong(request.get("registerId"))));
        return result;
    }

    @Transactional
    public Map<String, Object> createInspectionRequest(Map<String, Object> request) {
        assertRegisterAccess(toLong(request.get("registerId")));
        Map<String, Object> result = createTechnologyRequest(request, "inspection");
        markExamPending(toLong(request.get("registerId")));
        result.put("visitState", currentVisitState(toLong(request.get("registerId"))));
        return result;
    }

    @Transactional
    public Map<String, Object> createDisposalRequest(Map<String, Object> request) {
        assertRegisterAccess(toLong(request.get("registerId")));
        return createTechnologyRequest(request, "disposal");
    }

    public List<Map<String, Object>> getCheckResults(Long registerId) {
        assertRegisterAccess(registerId);
        return physicianMapper.selectCheckResults(registerId).stream()
            .map(this::withAiAnalysis)
            .map(this::withImagingAnalysis)
            .toList();
    }

    public byte[] getCheckImagingNrrd(Long checkRequestId) {
        Map<String, Object> context = physicianMapper.selectCheckImagingContext(checkRequestId);
        if (context == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        Long registerId = toLong(context.get("registerId"));
        assertRegisterAccess(registerId);
        String volumeId = context.get("imagingVolumeId") == null
            ? null
            : String.valueOf(context.get("imagingVolumeId")).trim();
        if (volumeId == null || volumeId.isEmpty()) {
            throw new BusinessException(400, "该检查单尚未绑定 CT 影像");
        }
        return ctViewerClient.fetchVolumeNrrd(volumeId);
    }

    public Map<String, Object> getCheckImagingMeta(Long checkRequestId) {
        Map<String, Object> context = physicianMapper.selectCheckImagingContext(checkRequestId);
        if (context == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        Long registerId = toLong(context.get("registerId"));
        assertRegisterAccess(registerId);
        String volumeId = context.get("imagingVolumeId") == null
            ? null
            : String.valueOf(context.get("imagingVolumeId")).trim();
        if (volumeId == null || volumeId.isEmpty()) {
            throw new BusinessException(400, "该检查单尚未绑定 CT 影像");
        }
        return ctViewerClient.fetchVolumeMeta(volumeId);
    }

    public Map<String, Object> getCheckResultForm(Long checkRequestId) {
        Map<String, Object> context = physicianMapper.selectCheckRequestResultFormContext(checkRequestId);
        if (context == null) {
            throw new BusinessException(404, "检查申请不存在");
        }
        Long registerId = toLong(context.get("registerId"));
        assertRegisterAccess(registerId);
        return buildCheckResultFormSchema(context);
    }

    private Map<String, Object> buildCheckResultFormSchema(Map<String, Object> context) {
        String aiCategoryCode = context.get("aiCategoryCode") == null
            ? null
            : String.valueOf(context.get("aiCategoryCode"));
        String categoryCode = resolveResultFormCategoryCode(aiCategoryCode);
        List<Map<String, Object>> baseFields = physicianMapper.selectResultFormFieldsByOwner("category", categoryCode);
        if (baseFields.isEmpty()) {
            baseFields = defaultCategoryFields(categoryCode);
        }
        Long techId = toLong(context.get("medicalTechnologyId"));
        List<Map<String, Object>> extensionFields = techId == null
            ? List.of()
            : physicianMapper.selectResultFormFieldsByOwner("tech_extension", String.valueOf(techId));

        List<Map<String, Object>> merged = new java.util.ArrayList<>(baseFields);
        merged.addAll(extensionFields);

        Map<String, Object> category = physicianMapper.selectResultFormCategoryByCode(categoryCode);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("checkRequestId", toLong(context.get("checkRequestId")));
        schema.put("categoryCode", categoryCode);
        schema.put("categoryName", category == null ? categoryCode : category.get("categoryName"));
        schema.put("medicalTechnologyId", techId);
        schema.put("techCode", context.get("techCode"));
        schema.put("techName", context.get("techName"));
        schema.put("fields", merged);
        schema.put("baseFieldCount", baseFields.size());
        schema.put("extensionFieldCount", extensionFields.size());
        schema.put("existingValues", parseExistingResultValues(
            context.get("checkResult") == null ? null : String.valueOf(context.get("checkResult"))
        ));
        return schema;
    }

    private String resolveResultFormCategoryCode(String aiCategoryCode) {
        if (aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct")) {
            return "imaging_ct";
        }
        return "general_check";
    }

    private List<Map<String, Object>> defaultCategoryFields(String categoryCode) {
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("fieldKey", "imaging_ct".equals(categoryCode) ? "findings" : "checkResult");
        primary.put("fieldLabel", "imaging_ct".equals(categoryCode) ? "所见" : "检查结果");
        primary.put("fieldType", "textarea");
        primary.put("required", true);
        primary.put("sortOrder", 1);
        if ("imaging_ct".equals(categoryCode)) {
            Map<String, Object> impression = new LinkedHashMap<>();
            impression.put("fieldKey", "impression");
            impression.put("fieldLabel", "印象");
            impression.put("fieldType", "textarea");
            impression.put("required", true);
            impression.put("sortOrder", 2);
            Map<String, Object> conclusion = new LinkedHashMap<>();
            conclusion.put("fieldKey", "conclusion");
            conclusion.put("fieldLabel", "结论");
            conclusion.put("fieldType", "textarea");
            conclusion.put("required", true);
            conclusion.put("sortOrder", 3);
            return List.of(primary, impression, conclusion);
        }
        return List.of(primary);
    }

    private Map<String, Object> parseExistingResultValues(String storedResult) {
        if (storedResult == null || storedResult.isBlank()) {
            return Map.of();
        }
        String trimmed = storedResult.trim();
        if (!trimmed.startsWith("{")) {
            return Map.of("checkResult", trimmed);
        }
        try {
            Map<String, Object> payload = JSON.readValue(trimmed, new TypeReference<>() {});
            Object values = payload.get("values");
            if (values instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            if (payload.containsKey("legacyText")) {
                return Map.of("checkResult", payload.get("legacyText"));
            }
        } catch (JsonProcessingException ignored) {
            return Map.of("checkResult", trimmed);
        }
        return Map.of();
    }

    public List<Map<String, Object>> getInspectionResults(Long registerId) {
        assertRegisterAccess(registerId);
        return physicianMapper.selectInspectionResults(registerId).stream().map(this::withAiAnalysis).toList();
    }

    public List<Map<String, Object>> getDiseases(String keyword) {
        return physicianMapper.selectDiseases(keyword);
    }

    @Transactional
    public void submitDiagnosis(Map<String, Object> request) {
        Long registerId = physicianMapper.selectRegisterIdByMedicalRecordId(toLong(request.get("medicalRecordId")));
        assertRegisterAccess(registerId);
        paymentClient.assertAllPaid(registerId);
        persistDiagnosisFields(request);
    }

    @Transactional
    public void saveDiagnosisDraft(Map<String, Object> request) {
        Long registerId = physicianMapper.selectRegisterIdByMedicalRecordId(toLong(request.get("medicalRecordId")));
        assertRegisterAccess(registerId);
        persistDiagnosisFields(request);
    }

    private void persistDiagnosisFields(Map<String, Object> request) {
        physicianMapper.updateDiagnosis(request);
        Long medicalRecordId = toLong(request.get("medicalRecordId"));
        syncRecordDiseases(medicalRecordId, diseaseIds(request));
    }

    public List<Map<String, Object>> getDiagnosisList(Long registrationId) {
        assertRegisterAccess(registrationId);
        return physicianMapper.selectDiagnosisSuggestions(registrationId);
    }

    public Map<String, Object> createDiagnosis(Map<String, Object> diagnosisRequest) {
        submitDiagnosis(diagnosisRequest);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "saved");
        return result;
    }

    public List<Map<String, Object>> getDrugs(String keyword) {
        return physicianMapper.selectDrugs(keyword);
    }

    public Map<String, Object> getDrugsPage(String keyword, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        int offset = (safePage - 1) * safeSize;
        long total = physicianMapper.countDrugs(keyword);
        List<Map<String, Object>> list = physicianMapper.selectDrugsPage(keyword, offset, safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("pageSize", safeSize);
        return result;
    }

    public Map<String, Object> getDrug(Long id) {
        return physicianMapper.selectDrugById(id);
    }

    public List<Map<String, Object>> getPrescriptionList(Long registrationId) {
        assertRegisterAccess(registrationId);
        return physicianMapper.selectPrescriptions(registrationId);
    }

    @Transactional
    public Map<String, Object> createPrescription(Map<String, Object> prescriptionRequest) {
        Long registerId = toLong(prescriptionRequest.get("registerId"));
        assertRegisterAccess(registerId);
        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        Long patientId = toLong(register.get("patientId"));
        String patientName = register.get("realName") != null ? register.get("realName").toString() : null;

        List<Map<String, Object>> items = requestItems(prescriptionRequest);
        String diagnosis = prescriptionRequest.get("confirmedDiagnosis") == null
            ? null
            : String.valueOf(prescriptionRequest.get("confirmedDiagnosis")).trim();
        List<Long> prescriptionIds = items.stream().map(item -> {
            Map<String, Object> row = new HashMap<>(item);
            row.put("registerId", registerId);
            if (diagnosis != null && !diagnosis.isEmpty()) {
                row.put("diagnosis", diagnosis);
            }
            physicianMapper.insertPrescription(row);
            return toLong(row.get("id"));
        }).toList();

        // 处方明细（含 drugName/drugPrice/drugNumber），用于汇总金额与发通知
        List<Map<String, Object>> prescriptions = physicianMapper.selectPrescriptions(registerId);
        BigDecimal totalAmount = prescriptions.stream()
            .map(item -> toDecimal(item.get("drugPrice")).multiply(new BigDecimal(String.valueOf(item.getOrDefault("drugNumber", "0")))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 推送药品费到 payment-service（幂等：ON CONFLICT DO NOTHING）。
        // 即使金额为 0，payment-service 也会落 status=1（已结清），避免后续 assertAllPaid 卡住。
        if (patientId != null) {
            paymentClient.createMedicationFee(registerId, patientId, patientName, totalAmount);
        }

        // 直接结束看诊（不走 endVisit 内部的 assertAllPaid）。
        // 设计取舍：用户希望保持"开药即归档"的原体验，药品费/检查费等费用作为挂账留在支付中心，
        // 患者之后自行缴费 —— 不让缴费流程卡住医生端的归档动作。
        int current = currentVisitState(registerId);
        if (current == VISIT_IN_PROGRESS || current == VISIT_EXAM_PENDING || current == VISIT_EXAM_COMPLETED) {
            physicianMapper.updateVisitState(registerId, VISIT_ENDED);
        }

        // 事务 commit 后发通知，避免回滚后误推
        registerPrescriptionSubmittedNotificationIfTxActive(
                patientId, patientName, registerId, prescriptions, totalAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prescriptionIds", prescriptionIds);
        result.put("totalAmount", totalAmount);
        result.put("confirmedDiagnosis", prescriptionRequest.get("confirmedDiagnosis"));
        result.put("visitState", currentVisitState(registerId));
        return result;
    }

    /**
     * 处方开具后给患者推"处方已开具"通知（事务 commit 后才发，避免回滚后误推）。
     * <p>处方费用已推送到 payment-service（MEDICATION_FEE），所以通知里提示"请前往支付中心缴费"，
     * 缴费完成后 payment-service 会再推一条 PAYMENT_SUCCESS 通知（含"凭电子凭证到药房取药"提示）。
     * <p>通知失败仅 log，绝不影响开处方主流程（NotificationClient.trySend 内部已吞异常）。
     * <p>若调用时不在事务里（理论上不会），直接同步发送。
     */
    private void registerPrescriptionSubmittedNotificationIfTxActive(
            Long patientId, String patientName, Long registerId,
            List<Map<String, Object>> prescriptions, BigDecimal totalAmount) {
        if (patientId == null || prescriptions.isEmpty()) return;

        final String greeting = (patientName == null || patientName.isBlank())
                ? "您好" : "尊敬的 " + patientName;
        final int drugCount = prescriptions.size();
        // 药品名拼接（最多列 3 个，超出的显示「等 N 种药品」），避免通知过长
        final String drugSummary;
        List<String> names = prescriptions.stream()
                .map(p -> p.get("drugName") != null ? p.get("drugName").toString() : "未命名药品")
                .toList();
        if (names.size() <= 3) {
            drugSummary = String.join("、", names);
        } else {
            drugSummary = names.subList(0, 3).stream().reduce((a, b) -> a + "、" + b).orElse("")
                    + " 等 " + names.size() + " 种药品";
        }
        final String totalStr = totalAmount != null ? totalAmount.toPlainString() : "0.00";
        final String content = String.format(
                "%s，您的处方已开具（共 %d 种药品：%s），处方总额 %s 元，请前往支付中心完成缴费，缴费后凭电子凭证到药房取药。",
                greeting, drugCount, drugSummary, totalStr);

        Runnable send = () -> notificationClient.trySend(
                patientId, "patient", "PRESCRIPTION_CREATED",
                "处方已开具", content,
                "register", registerId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    public void deletePrescription(Long id) {
        physicianMapper.deletePrescription(id);
    }

    public List<Map<String, Object>> getExamSuggestions(Long registerId) {
        assertRegisterAccess(registerId);
        return physicianMapper.selectExamSuggestions(registerId);
    }

    public List<Map<String, Object>> getDiagnosisSuggestions(Long registerId) {
        assertRegisterAccess(registerId);
        return physicianMapper.selectDiagnosisSuggestions(registerId);
    }

    public Map<String, Object> getPrescriptionReview(Long registerId) {
        assertRegisterAccess(registerId);
        return fallbackReview(registerId);
    }

    public Map<String, Object> getDifyWorkflowContracts() {
        return Map.of(
            "w1Structure", Map.of(
                "inputs", List.of("registerId", "inputMode", "longText", "doctorForm", "structuredRecord", "patientInfoFromRegister"),
                "outputs", List.of("registerId", "patientInfo", "chiefComplaint", "symptomDuration", "presentIllness", "history", "allergy", "physique", "preliminaryImpression")
            ),
            "w2Recommend", Map.of(
                "inputs", List.of("clinical_context_json", "available_examinations_json"),
                "outputs", List.of(
                    "preliminaryAssessment",
                    "recommendedExaminations",
                    "notRecommendedNote",
                    "unmatchedSuggestions"
                )
            ),
            "w2bSimulate", Map.of(
                "inputs", List.of("registerId", "structuredRecord", "orderedExaminations", "simulationProfile"),
                "outputs", List.of("simulatedResults")
            ),
            "w3Analyze", Map.of(
                "inputs", List.of(
                    "registerId",
                    "structuredRecordJson",
                    "allResultsJson",
                    "preliminaryAssessment"
                ),
                "outputs", List.of(
                    "registerId",
                    "clinicalImpression",
                    "examSummaries",
                    "overallAnalysis",
                    "explicitNonDiagnosis"
                )
            ),
            "w4Diagnose", Map.of(
                "inputs", List.of(
                    "register_id",
                    "patient_info_text",
                    "chief_complaint",
                    "present_illness",
                    "past_history",
                    "allergy_history",
                    "preliminary_diagnosis_text",
                    "preliminary_diseases_text",
                    "check_results_text",
                    "inspection_results_text",
                    "w3_analysis_text",
                    "abnormal_indicators_text",
                    "ai_previsit_summary",
                    "doctor_notes"
                ),
                "outputs", List.of(
                    "status",
                    "registerId",
                    "suggestions",
                    "fallbackSuggestions",
                    "clinicalSummaryForDoctor",
                    "differentialDiagnosis",
                    "warningSigns",
                    "searchAdvice"
                )
            ),
            "w5RecommendDrugs", Map.of(
                "inputs", List.of(
                    "register_id",
                    "patient_info_text",
                    "confirmed_diagnosis_text",
                    "w4_suggestions_text",
                    "allergy_history",
                    "past_history",
                    "chief_complaint",
                    "w3_analysis_text",
                    "abnormal_indicators_text",
                    "preliminary_diagnosis_text",
                    "doctor_notes"
                ),
                "outputs", List.of(
                    "status",
                    "registerId",
                    "suggestions",
                    "fallbackSuggestions",
                    "clinicalSummaryForDoctor",
                    "allergyWarnings",
                    "searchAdvice"
                )
            ),
            "preliminaryDiagnosis", Map.of(
                "inputs", List.of("registerId", "text", "preHandle", "model"),
                "outputs", List.of("diagnosisText", "diagnosisBasis", "confidence", "suggestedDiseases", "modelId", "llmModel")
            ),
            "ctModel", getCtModelOutputContract()
        );
    }

    public Map<String, Object> getCtModelOutputContract() {
        return Map.of(
            "input", Map.of(
                "required", List.of("checkRequestId", "registerId", "dicomSeriesOrImageUri"),
                "optional", List.of("age", "gender", "chiefComplaint", "checkPosition", "checkPurpose")
            ),
            "output", Map.of(
                "hasAbnormality", "boolean",
                "abnormalProbability", "number",
                "riskLevel", List.of("normal", "attention", "warning", "danger"),
                "findings", List.of(Map.of(
                    "findingType", "string",
                    "anatomicalLocation", "string",
                    "size", "string",
                    "severity", "string",
                    "confidence", "number",
                    "bbox", "optional",
                    "maskUri", "optional"
                )),
                "aiImpression", "string",
                "limitations", "string"
            ),
            "databaseMapping", Map.of(
                "originalResult", "ai_exam_analysis.original_result",
                "findings", "ai_exam_analysis.abnormal_indicators",
                "riskLevel", "ai_exam_analysis.risk_level",
                "aiImpression", "ai_exam_analysis.analysis_report",
                "correlationAnalysis", "ai_exam_analysis.correlation_analysis"
            )
        );
    }

    private void assertRegisterAccess(Long registerId) {
        if (registerId == null) {
            throw new BusinessException(400, "挂号记录不存在");
        }
        if (physicianMapper.isRegisterPatientArchived(registerId)) {
            throw new BusinessException(403, "该患者档案已归档，无法在医疗系统中访问");
        }
        if (AgentToolExecutionContext.isActive()) {
            Long ownerEmployeeId = physicianMapper.selectRegisterEmployeeId(registerId);
            if (ownerEmployeeId == null) {
                throw new BusinessException(400, "挂号记录不存在");
            }
            Long contextDoctorId = AgentToolExecutionContext.getDoctorId();
            if (contextDoctorId != null && !PhysicianAuthContext.isAdminAllAccess()
                && !ownerEmployeeId.equals(contextDoctorId)) {
                throw new BusinessException(403, "无权访问该患者");
            }
            return;
        }
        if (PhysicianAuthContext.isAdminAllAccess()) {
            return;
        }
        Long currentEmployeeId = PhysicianAuthContext.employeeIdOrNull();
        if (currentEmployeeId == null) {
            throw new BusinessException(403, "无权访问该患者");
        }
        Long ownerEmployeeId = physicianMapper.selectRegisterEmployeeId(registerId);
        if (ownerEmployeeId == null || !ownerEmployeeId.equals(currentEmployeeId)) {
            throw new BusinessException(403, "无权访问该患者");
        }
    }

    private void markExamPending(Long registerId) {
        if (registerId == null) {
            return;
        }
        int current = currentVisitState(registerId);
        if (current == VISIT_IN_PROGRESS || current == VISIT_EXAM_COMPLETED) {
            physicianMapper.updateVisitState(registerId, VISIT_EXAM_PENDING);
        }
    }

    private Map<String, Object> syncExamStateIfNeeded(Map<String, Object> row) {
        Long registerId = toLong(row.get("registerId"));
        int state = toInt(row.get("visitState"));
        if (registerId != null && state == VISIT_EXAM_PENDING) {
            int synced = syncExamCompletedState(registerId);
            row.put("visitState", synced);
        }
        return row;
    }

    private int syncExamCompletedState(Long registerId) {
        int current = currentVisitState(registerId);
        if (current != VISIT_EXAM_PENDING) {
            return current;
        }
        if (physicianMapper.countPendingExamRequests(registerId) == 0) {
            physicianMapper.updateVisitState(registerId, VISIT_EXAM_COMPLETED);
            return VISIT_EXAM_COMPLETED;
        }
        return VISIT_EXAM_PENDING;
    }

    private int currentVisitState(Long registerId) {
        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (register == null) {
            return VISIT_REGISTERED;
        }
        return toInt(register.get("visitState"));
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return VISIT_REGISTERED;
    }

    private Map<String, Object> withAiConsultSummary(Map<String, Object> row) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chiefComplaint", row.remove("chiefComplaint"));
        summary.put("symptomDuration", row.remove("symptomDuration"));
        summary.put("historySummary", row.remove("historySummary"));
        summary.put("allergySummary", row.remove("allergySummary"));
        summary.put("medicationSummary", row.remove("medicationSummary"));
        summary.put("aiSummary", row.remove("aiSummary"));
        summary.put("suggestedExam", row.remove("suggestedExam"));
        row.put("aiConsultSummary", summary.values().stream().allMatch(Objects::isNull) ? null : summary);
        return row;
    }

    private Map<String, Object> withAiAnalysis(Map<String, Object> row) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("riskLevel", row.remove("riskLevel"));
        analysis.put("analysisReport", row.remove("analysisReport"));
        analysis.put("abnormalIndicators", row.remove("abnormalIndicators"));
        analysis.put("correlationAnalysis", row.remove("correlationAnalysis"));
        row.put("aiAnalysis", analysis.values().stream().allMatch(Objects::isNull) ? null : analysis);
        return row;
    }

    private Map<String, Object> withImagingAnalysis(Map<String, Object> row) {
        Object raw = row.remove("imagingAnalysisResultRaw");
        if (raw instanceof String json && !json.isBlank()) {
            try {
                Map<String, Object> parsed = JSON.readValue(json, new TypeReference<>() {});
                row.put("imagingAnalysisResult", parsed);
            } catch (JsonProcessingException ex) {
                row.put("imagingAnalysisResult", null);
            }
        }
        Object hasAnalysis = row.get("hasImagingAnalysis");
        if (hasAnalysis instanceof Boolean bool) {
            row.put("hasImagingAnalysis", bool);
        } else if (row.get("imagingAnalysisResult") != null) {
            row.put("hasImagingAnalysis", true);
        }
        return row;
    }

    private Map<String, Object> copyRecordFields(Map<String, Object> request) {
        Map<String, Object> record = new HashMap<>();
        record.put("registerId", toLong(request.get("registerId")));
        record.put("readme", request.get("readme"));
        record.put("present", request.get("present"));
        record.put("presentTreat", request.get("presentTreat"));
        record.put("history", request.get("history"));
        record.put("allergy", request.get("allergy"));
        record.put("physique", request.get("physique"));
        record.put("proposal", request.get("proposal"));
        return record;
    }

    private Map<String, Object> createTechnologyRequest(Map<String, Object> request, String type) {
        Long registerId = toLong(request.get("registerId"));
        Map<String, Object> register = physicianMapper.selectRegisterById(registerId);
        if (register == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        Long patientId = toLong(register.get("patientId"));
        String patientName = register.get("realName") != null ? register.get("realName").toString() : null;
        String itemCode = itemCodeForTechType(type);

        // 收集通知参数（等事务 commit 后再发送，避免事务回滚后误推消息）
        List<Map<String, Object>> pendingNotifications = new java.util.ArrayList<>();

        List<Long> requestIds = requestItems(request).stream().map(item -> {
            Map<String, Object> row = new HashMap<>(item);
            row.put("registerId", registerId);
            Long techId = toLong(row.get("medicalTechnologyId"));
            Map<String, Object> tech = techId != null ? physicianMapper.selectMedicalTechnologyById(techId) : null;
            if (tech == null) {
                throw new BusinessException(400, "医技项目不存在: " + techId);
            }

            if ("check".equals(type)) {
                physicianMapper.insertCheckRequest(row);
            } else if ("inspection".equals(type)) {
                physicianMapper.insertInspectionRequest(row);
            } else {
                physicianMapper.insertDisposalRequest(row);
            }
            Long requestId = toLong(row.get("id"));
            BigDecimal unitPrice = toBigDecimal(tech.get("techPrice"));
            String techName = tech.get("techName") != null ? tech.get("techName").toString() : itemCode;
            paymentClient.createTechFee(
                    registerId, patientId, patientName, itemCode, requestId, techId, techName, unitPrice);

            // 收集该 item 的通知参数（事务 afterCommit 时发送）
            Map<String, Object> n = new HashMap<>();
            n.put("techName", techName);
            n.put("unitPrice", unitPrice != null ? unitPrice.toPlainString() : "0");
            n.put("itemCode", itemCode);
            pendingNotifications.add(n);

            return requestId;
        }).toList();

        // afterCommit：所有 item 写完 + 事务 commit 后才发通知，避免回滚后误推
        registerFeePendingNotificationsIfTxActive(
                patientId, patientName, registerId, pendingNotifications);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestIds", requestIds);
        return result;
    }

    /**
     * 在事务 commit 之后给患者推"待缴费提醒"通知。
     * <p>每个开单项一条通知，让患者清楚知道哪些项目待缴费。
     * <p>通知失败仅 log，绝不影响开单主流程（NotificationClient.trySend 内部已吞异常）。
     * <p>若调用时不在事务里（理论上不会），直接同步发送。
     */
    private void registerFeePendingNotificationsIfTxActive(
            Long patientId, String patientName, Long registerId,
            List<Map<String, Object>> items) {
        if (items.isEmpty() || patientId == null) return;

        final String greeting = (patientName == null || patientName.isBlank())
                ? "您好" : "尊敬的 " + patientName;

        Runnable sendAll = () -> {
            for (Map<String, Object> item : items) {
                String techName = String.valueOf(item.get("techName"));
                String price = String.valueOf(item.get("unitPrice"));
                String tip = paymentTipByItemCode(String.valueOf(item.get("itemCode")));
                String content = String.format(
                        "%s，您的「%s」已开单，金额 %s 元，请前往支付中心完成缴费，缴费后医技科室方可执行。",
                        greeting, techName, price);
                if (tip != null) content = content + tip;
                notificationClient.trySend(
                        patientId, "patient", "EXAM_FEE_CREATED",
                        "待缴费提醒", content,
                        "register", registerId);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAll.run();
                }
            });
        } else {
            sendAll.run();
        }
    }

    /** 按 itemCode 给待缴费通知补一句业务提示语。 */
    private String paymentTipByItemCode(String itemCode) {
        if (itemCode == null) return null;
        return switch (itemCode) {
            case "CHECK_FEE", "EXAMINATION_FEE" -> "缴费后请按预约时间前往相应检查科室。";
            case "INSPECTION_FEE" -> "缴费后请前往检验科完成检验。";
            case "DISPOSAL_FEE" -> "缴费后请按医嘱前往相应科室完成处置。";
            default -> null;
        };
    }

    private static String itemCodeForTechType(String type) {
        return switch (type) {
            case "check" -> "CHECK_FEE";
            case "inspection" -> "INSPECTION_FEE";
            case "disposal" -> "DISPOSAL_FEE";
            default -> throw new BusinessException(400, "不支持的医技类型: " + type);
        };
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestItems(Map<String, Object> request) {
        Object items = request.get("items");
        if (items instanceof List<?>) {
            return ((List<?>) items).stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private void syncRecordDiseases(Long medicalRecordId, List<Long> diseaseIds) {
        if (medicalRecordId == null) {
            return;
        }
        physicianMapper.deleteMedicalRecordDiseases(medicalRecordId);
        diseaseIds.forEach(diseaseId -> physicianMapper.insertMedicalRecordDisease(medicalRecordId, diseaseId));
    }

    private List<Long> diseaseIds(Map<String, Object> request) {
        Object value = request.get("diseaseIds");
        if (value instanceof List<?>) {
            return ((List<?>) value).stream().map(this::toLong).filter(Objects::nonNull).toList();
        }
        return List.of();
    }

    private Map<String, Object> loadPreliminaryAiMeta(Long registerId) {
        Map<String, Object> log = physicianMapper.selectLatestAiMedicalRecordLogBySourceType(registerId, "preliminary_diagnosis");
        if (log == null) {
            return Map.of();
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("aiDiagnosis", log.get("aiDiagnosis"));
        meta.put("modelId", log.get("modelId"));
        Object raw = log.get("doctorModification");
        if (raw instanceof String text && !text.isBlank()) {
            try {
                Map<String, Object> parsed = JSON.readValue(text, new TypeReference<>() {
                });
                meta.putAll(parsed);
            } catch (Exception ignored) {
                // ignore malformed meta
            }
        }
        return meta;
    }

    private Map<String, Object> fallbackReview(Long registerId) {
        Map<String, Object> stored = physicianMapper.selectLatestPrescriptionReview(registerId);
        if (stored != null) {
            return stored;
        }
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("reviewResult", "passed");
        review.put("riskScore", 0);
        review.put("riskDetails", null);
        review.put("status", "not_called");
        return review;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        return BigDecimal.ZERO;
    }
}
