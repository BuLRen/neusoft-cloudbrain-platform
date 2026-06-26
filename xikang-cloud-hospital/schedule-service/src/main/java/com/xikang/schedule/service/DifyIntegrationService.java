package com.xikang.schedule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.result.Result;
import com.xikang.schedule.client.RegistrationClient;
import com.xikang.schedule.client.dto.DepartmentDTO;
import com.xikang.schedule.client.dto.EmployeeDTO;
import com.xikang.schedule.client.dto.RegistLevelDTO;
import com.xikang.schedule.dto.AiGeneratePlanRequest;
import com.xikang.schedule.dto.AiGeneratePlanResult;
import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.LeaveRequest;
import com.xikang.schedule.entity.SchedulePlan;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dify 集成服务（替代原 CozeIntegrationService）
 * <p>调用 Dify 工作流 {@code POST /v1/workflows/run}，blocking 模式。
 * 开始节点不支持 Array/Object，复合类型字段统一 JSON.stringify 成 String 传入。</p>
 */
@Slf4j
@Service
public class DifyIntegrationService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DEFAULT_GENERATE_TYPE = "full";
    private static final int DEFAULT_AI_VERSION = 1;

    /** Dify 调用阶段进度区间（blocking 模式下进度从 MIN 直跳到 MAX） */
    private static final int DIFY_PHASE_PERCENT_MIN = 30;
    private static final int DIFY_PHASE_PERCENT_MAX = 85;

    private final RegistrationClient registrationClient;
    private final SchedulePlanService schedulePlanService;
    private final DoctorScheduleService doctorScheduleService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${dify.api-key:}")
    private String difyApiKey;

    @Value("${dify.base-url:http://43.139.102.203}")
    private String difyBaseUrl;

    @Value("${dify.timeout-ms:660000}")
    private Long difyTimeoutMs;

    public DifyIntegrationService(RegistrationClient registrationClient,
                                   SchedulePlanService schedulePlanService,
                                   DoctorScheduleService doctorScheduleService,
                                   ObjectMapper objectMapper,
                                   WebClient.Builder webClientBuilder) {
        this.registrationClient = registrationClient;
        this.schedulePlanService = schedulePlanService;
        this.doctorScheduleService = doctorScheduleService;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void logDifyConfigurationOnStartup() {
        logDifyConfiguration("startup");
    }

    /**
     * 处理医生请假（AI 理解 + 生成调整方案）—— 预留
     */
    public void processLeaveWithAI(LeaveRequest leaveRequest) {
        log.info("【预留】Dify AI 处理请假：leaveId={}, physicianId={}",
                leaveRequest.getId(), leaveRequest.getPhysicianId());
    }

    /**
     * 分析号源分布 —— 预留
     */
    public void analyzeQuotaDistribution(Long departmentId, int daysAhead) {
        log.info("【预留】Dify AI 分析号源分布：departmentId={}, days={}", departmentId, daysAhead);
    }

    /**
     * 理解自然语言请假请求 —— 预留
     */
    public String parseLeaveRequest(String rawText) {
        log.info("【预留】Dify AI 理解自然语言请假：{}", rawText);
        return "{}";
    }

    /**
     * 获取 Dify API 配置状态
     */
    public boolean isConfigured() {
        return StringUtils.hasText(difyApiKey)
                && StringUtils.hasText(difyBaseUrl);
    }

    private void logDifyConfiguration(String stage) {
        log.info("Dify config [{}]: apiKeyPresent={}, baseUrl='{}', timeoutMs={}",
                stage,
                StringUtils.hasText(difyApiKey),
                difyBaseUrl,
                difyTimeoutMs);
    }

    // ==================== 异步编排入口（AiGenerateTaskService 调用） ====================

    /**
     * 编排阶段：参数校验 → 拉科室医生 → 调 Dify → 解析。
     * <p>无 @Transactional，由 AiGenerateTaskService 在异步线程里调用。
     * 落库动作通过 {@link #persistAiPlanAndSchedules} 单独事务完成。</p>
     */
    public AiGeneratePlanResult orchestrate(AiGeneratePlanRequest request,
                                            Consumer<StageProgress> progressSink) {
        emitStage(progressSink, "validating", 5, "参数校验");
        validateGenerateRequest(request);

        if (!isConfigured()) {
            logDifyConfiguration("orchestrate");
            throw new RuntimeException("Dify 工作流配置不完整，请检查 schedule-service 的 dify 配置");
        }

        String month = normalizeMonth(request.getMonth());

        emitStage(progressSink, "loading_doctors", 20, "加载医生数据");
        DepartmentDTO department = getDepartment(request.getDepartmentId());
        List<EmployeeDTO> doctors = getDoctors(request.getDepartmentId());
        if (doctors.isEmpty()) {
            throw new RuntimeException("当前科室暂无可用于生成排班的医生");
        }
        Map<Long, RegistLevelDTO> registLevels = getRegistLevelMap();

        Map<String, Object> workflowInput = buildWorkflowInput(
                request.getDepartmentId(), department, month, doctors);

        emitStage(progressSink, "calling_dify", DIFY_PHASE_PERCENT_MIN, "调用 Dify 工作流");
        String rawResponse = callDifyWorkflow(workflowInput, progressSink);
        log.info("Dify raw response [len={}]", rawResponse == null ? 0 : rawResponse.length());

        emitStage(progressSink, "parsing_ai", 88, "解析 AI 返回");
        AiGeneratePlanResult result = parseWorkflowResult(rawResponse);

        emitStage(progressSink, "ready_to_save", 92, "准备保存");
        List<DoctorSchedule> schedules = buildDoctorSchedules(
                request.getDepartmentId(), result.getValidatedSchedules(), doctors, registLevels);
        if (schedules.isEmpty()) {
            throw new RuntimeException("AI 未生成可保存的排班数据");
        }
        result.setSchedulesForPersist(schedules);
        return result;
    }

    /**
     * 落库阶段：创建/复用计划 + 批量插排班。独立事务。
     */
    @Transactional
    public AiGeneratePlanResult persistAiPlanAndSchedules(AiGeneratePlanRequest request,
                                                           AiGeneratePlanResult result,
                                                           Consumer<StageProgress> progressSink) {
        List<DoctorSchedule> schedules = result.getSchedulesForPersist();
        if (schedules == null || schedules.isEmpty()) {
            throw new RuntimeException("没有可落库的排班数据");
        }
        DepartmentDTO department = getDepartment(request.getDepartmentId());
        String month = normalizeMonth(request.getMonth());

        emitStage(progressSink, "saving_plan", 95, "保存排班");
        SchedulePlan plan = schedulePlanService.createOrReuseAiDraftPlan(
                request.getDepartmentId(),
                month,
                department.getName(),
                request.getOperatorId(),
                resolveAiVersion(result),
                schedules.size(),
                schedules.stream().map(DoctorSchedule::getTotalQuota)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).sum()
        );

        schedules.forEach(schedule -> schedule.setPlanId(plan.getId()));
        doctorScheduleService.batchCreate(schedules, plan.getId());

        SchedulePlan refreshedPlan = schedulePlanService.getPlanById(plan.getId());
        result.setPlanId(plan.getId());
        result.setPlan(refreshedPlan);
        result.setScheduleCount(schedules.size());
        result.setAiVersion(resolveAiVersion(result));
        result.setGenerateType(StringUtils.hasText(request.getGenerateType())
                ? request.getGenerateType() : DEFAULT_GENERATE_TYPE);
        if (!StringUtils.hasText(result.getMessage())) {
            result.setMessage("排班方案已生成，共" + schedules.size() + "条");
        }
        return result;
    }

    /**
     * 阶段事件载体。
     */
    public record StageProgress(String stage, int percent, String message) {
    }

    private void emitStage(Consumer<StageProgress> sink, String stage, int percent, String message) {
        if (sink == null) {
            return;
        }
        try {
            sink.accept(new StageProgress(stage, percent, message));
        } catch (RuntimeException ignored) {
            // 进度回调失败不能影响主流程
        }
    }

    // ==================== 解析与组装 ====================

    private void validateGenerateRequest(AiGeneratePlanRequest request) {
        if (request == null) {
            throw new RuntimeException("生成参数不能为空");
        }
        if (request.getDepartmentId() == null) {
            throw new RuntimeException("请选择科室");
        }
        if (!StringUtils.hasText(request.getMonth())) {
            throw new RuntimeException("请选择月份");
        }
        if (request.getOperatorId() == null) {
            throw new RuntimeException("缺少操作人信息");
        }
    }

    private String normalizeMonth(String month) {
        try {
            return YearMonth.parse(month, MONTH_FORMATTER).format(MONTH_FORMATTER);
        } catch (Exception exception) {
            throw new RuntimeException("月份格式错误，需为 yyyy-MM");
        }
    }

    private DepartmentDTO getDepartment(Long departmentId) {
        Result<List<DepartmentDTO>> result = registrationClient.getDepartments();
        List<DepartmentDTO> departments = unwrapResult(result, "获取科室列表失败");
        return departments.stream()
                .filter(department -> Objects.equals(department.getId(), departmentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到对应科室信息"));
    }

    private List<EmployeeDTO> getDoctors(Long departmentId) {
        Result<List<EmployeeDTO>> result = registrationClient.getDoctorsByDepartment(departmentId);
        return unwrapResult(result, "获取科室医生列表失败").stream()
                .filter(doctor -> doctor.getDelmark() == null || doctor.getDelmark() == 0)
                .collect(Collectors.toList());
    }

    private Map<Long, RegistLevelDTO> getRegistLevelMap() {
        Result<List<RegistLevelDTO>> result = registrationClient.getRegistLevels();
        List<RegistLevelDTO> levels = unwrapResult(result, "获取挂号级别失败");
        return levels.stream().collect(Collectors.toMap(RegistLevelDTO::getId, item -> item, (left, right) -> left));
    }

    private <T> List<T> unwrapResult(Result<List<T>> result, String defaultMessage) {
        if (result == null) {
            throw new RuntimeException(defaultMessage);
        }
        if (result.getCode() != 200) {
            throw new RuntimeException(StringUtils.hasText(result.getMessage()) ? result.getMessage() : defaultMessage);
        }
        return result.getData() == null ? Collections.emptyList() : result.getData();
    }

    /**
     * 构造 Dify 工作流 inputs。
     * <p>⚠️ Dify 开始节点的所有字段（包括 department_id）都是 text-input 类型，
     * 必须传 String。复合类型字段也要 JSON.stringify 成 String。
     * 字段命名与 WF-01 设计文档保持一致：physicians_json / weekday_patterns_json / holidays_json。</p>
     */
    private Map<String, Object> buildWorkflowInput(Long departmentId,
                                                   DepartmentDTO department,
                                                   String month,
                                                   List<EmployeeDTO> doctors) {
        Map<String, Object> input = new LinkedHashMap<>();
        // Dify 开始节点全是 text-input，所有字段统一传 String
        input.put("department_id", String.valueOf(departmentId));
        input.put("department_name", department.getName());
        input.put("month", month);
        // 复合类型字段 String 化（Dify 开始节点限制）
        input.put("physicians_json", toJsonString(
                doctors.stream().map(this::toPhysicianPayload).collect(Collectors.toList())));
        input.put("weekday_patterns_json", toJsonString(buildWeekdayPatterns(month, doctors)));
        input.put("holidays_json", toJsonString(buildHolidays(month)));
        return input;
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> toPhysicianPayload(EmployeeDTO doctor) {
        Map<String, Object> physician = new LinkedHashMap<>();
        physician.put("id", doctor.getId());
        physician.put("realname", doctor.getRealname());
        physician.put("position", StringUtils.hasText(doctor.getRegistName()) ? doctor.getRegistName() : "普通号");
        return physician;
    }

    private Map<String, Object> buildWeekdayPatterns(String month, List<EmployeeDTO> doctors) {
        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        Map<String, Object> patterns = new LinkedHashMap<>();
        int doctorCount = Math.max(doctors.size(), 1);
        int workdayQuota = doctorCount * 30;
        int weekendQuota = Math.max(doctorCount * 15, 1);

        patterns.put("周一", buildWeekdayPattern(workdayQuota, BigDecimal.valueOf(0.85)));
        patterns.put("周二", buildWeekdayPattern(workdayQuota, BigDecimal.valueOf(0.82)));
        patterns.put("周三", buildWeekdayPattern(workdayQuota, BigDecimal.valueOf(0.80)));
        patterns.put("周四", buildWeekdayPattern(workdayQuota, BigDecimal.valueOf(0.78)));
        patterns.put("周五", buildWeekdayPattern(workdayQuota, BigDecimal.valueOf(0.76)));
        if (yearMonth.lengthOfMonth() > 27) {
            patterns.put("周六", buildWeekdayPattern(weekendQuota, BigDecimal.valueOf(0.45)));
            patterns.put("周日", buildWeekdayPattern(0, BigDecimal.ZERO));
        }
        return patterns;
    }

    private Map<String, Object> buildWeekdayPattern(int avgQuota, BigDecimal usageRate) {
        Map<String, Object> pattern = new HashMap<>();
        pattern.put("avg_quota", avgQuota);
        pattern.put("usage_rate", usageRate);
        return pattern;
    }

    private List<String> buildHolidays(String month) {
        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        List<String> holidays = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                holidays.add(date.format(DATE_FORMATTER));
            }
        }
        return holidays;
    }

    /**
     * 调用 Dify 工作流（blocking 模式）。
     * <p>Dify blocking 模式一次性返回完整 JSON，无需 SSE 解析。
     * 响应结构：{@code { data: { outputs: {...}, status: "succeeded", elapsed_time, total_tokens } }}。</p>
     */
    private String callDifyWorkflow(Map<String, Object> workflowInput,
                                     Consumer<StageProgress> progressSink) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("inputs", workflowInput);
        requestBody.put("response_mode", "blocking");
        requestBody.put("user", "schedule-service");

        // 打印请求体用于排查 4xx（不打印 api-key）
        try {
            log.info("Dify request body: {}", objectMapper.writeValueAsString(requestBody));
        } catch (JsonProcessingException ignored) {
        }
        log.info("Dify api-key present: {}, startsWith app-: {}",
                StringUtils.hasText(difyApiKey),
                difyApiKey != null && difyApiKey.startsWith("app-"));

        WebClient client = webClientBuilder
                .baseUrl(trimTrailingSlash(difyBaseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + difyApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // blocking 模式：单次 HTTP 请求等到工作流跑完才返回完整结果
        // 注意：上层 gateway / vite 代理 / WebClient.timeout 都必须 >= Dify 实际执行时间
        try {
            String raw = client.post()
                    .uri("/v1/workflows/run")
                    .bodyValue(requestBody)
                    .exchangeToMono(response -> {
                        // 不管 200 还是 4xx/5xx，都把 body 读出来
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("[EMPTY BODY]")
                                .map(body -> {
                                    if (response.statusCode().isError()) {
                                        // 4xx/5xx：先 log 出 Dify 返回的真实错误内容，再抛
                                        log.error("Dify HTTP {} - response body: {}",
                                                response.statusCode(), body);
                                        throw new RuntimeException("Dify 返回 HTTP "
                                                + response.statusCode() + "，错误内容：" + body);
                                    }
                                    return body;
                                });
                    })
                    .timeout(java.time.Duration.ofMillis(difyTimeoutMs))
                    .block();
            emitStage(progressSink, "calling_dify", DIFY_PHASE_PERCENT_MAX,
                    "Dify 工作流执行完成");
            return raw;
        } catch (RuntimeException ex) {
            // 兜底：exchangeToMono 里抛的异常会带完整错误体
            throw ex;
        }
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 解析 Dify blocking 响应。结构：
     * <pre>{@code
     * {
     *   "task_id": "...",
     *   "workflow_run_id": "...",
     *   "data": {
     *     "id": "...",
     *     "workflow_id": "...",
     *     "status": "succeeded",
     *     "outputs": { "validated_schedules_json": "[...]", "statistics": {...}, "errors": [...], "warnings": [...], "message": "..." },
     *     "elapsed_time": 12.34,
     *     "total_tokens": 5678
     *   }
     * }
     * }</pre>
     */
    private AiGeneratePlanResult parseWorkflowResult(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new RuntimeException("Dify 返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // Dify 错误响应（如 API key 无效、工作流不存在）
            if (root.has("error")) {
                String errMsg = root.path("error").asText("Dify 返回错误");
                throw new RuntimeException("Dify 调用失败：" + errMsg);
            }

            JsonNode dataNode = root.path("data");
            String status = dataNode.path("status").asText("");
            if (!"succeeded".equalsIgnoreCase(status)) {
                String error = dataNode.path("error").asText("");
                throw new RuntimeException("Dify 工作流未成功：status=" + status
                        + (StringUtils.hasText(error) ? ", error=" + error : ""));
            }

            JsonNode outputs = dataNode.path("outputs");
            if (outputs.isMissingNode() || outputs.isNull()) {
                throw new RuntimeException("Dify 返回中缺少 outputs 字段");
            }

            // 排查用：把 data 节点的所有字段名 + outputs 的原始结构打出来
            java.util.List<String> dataFields = new java.util.ArrayList<>();
            dataNode.fieldNames().forEachRemaining(dataFields::add);
            log.info("Dify data fields: {}", dataFields);
            log.info("Dify outputs raw type={}, value (first 2000 chars)={}",
                    outputs.getNodeType(),
                    outputs.toString().substring(0, Math.min(outputs.toString().length(), 2000)));

            // outputs 内可能整体是 string（Dify 兼容模式下会字符串化），先解一层
            JsonNode outputsNode = outputs;
            if (outputsNode.isTextual()) {
                String text = outputsNode.asText();
                if (StringUtils.hasText(text) && looksLikeJson(text)) {
                    outputsNode = objectMapper.readTree(text);
                }
            }

            log.info("Dify outputs keys: {}",
                    outputsNode.isObject() ? outputsNode.fieldNames() : outputsNode.getNodeType());

            return parseFromPayload(outputsNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 Dify 返回结果失败", e);
        }
    }

    private AiGeneratePlanResult parseFromPayload(JsonNode payloadNode) {
        try {
            // 排查用：把 payloadNode 顶层所有字段名打出来
            java.util.List<String> payloadFields = new java.util.ArrayList<>();
            payloadNode.fieldNames().forEachRemaining(payloadFields::add);
            log.info("Dify outputs payload fields: {}", payloadFields);

            AiGeneratePlanResult result = objectMapper.treeToValue(payloadNode, AiGeneratePlanResult.class);
            if (result == null) {
                throw new RuntimeException("Dify 返回为空");
            }
            log.info("Parsed result: validatedSchedules.size={}, statistics={}, message={}",
                    result.getValidatedSchedules() == null ? "null" : result.getValidatedSchedules().size(),
                    result.getStatistics(),
                    result.getMessage());
            if (result.getValidatedSchedules() == null || result.getValidatedSchedules().isEmpty()) {
                log.info("validated_schedules is empty, falling back to manual parse");
                result.setValidatedSchedules(parseValidatedSchedules(payloadNode));
                log.info("After manual parse: validatedSchedules.size={}",
                        result.getValidatedSchedules() == null ? "null" : result.getValidatedSchedules().size());
            }
            if (result.getErrors() == null) {
                result.setErrors(Collections.emptyList());
            }
            if (result.getWarnings() == null) {
                result.setWarnings(Collections.emptyList());
            }
            if (!result.getErrors().isEmpty()) {
                throw new RuntimeException("AI 排班校验失败：" + stringify(result.getErrors()));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析 Dify 返回结果失败", e);
        }
    }

    private List<AiGeneratePlanResult.ValidatedScheduleDTO> parseValidatedSchedules(JsonNode payloadNode) throws JsonProcessingException {
        if (payloadNode == null) {
            return Collections.emptyList();
        }
        // v4.3：节点5 改为输出 validated_schedules_json（String，绕开 Dify 30 元素限制），
        //      但仍兼容老的 validated_schedules（Array）字段名
        JsonNode schedulesNode = payloadNode.get("validated_schedules_json");
        if (schedulesNode == null || schedulesNode.isNull() || schedulesNode.isMissingNode()) {
            schedulesNode = payloadNode.get("validated_schedules");
        }
        if (schedulesNode == null || schedulesNode.isNull() || schedulesNode.isMissingNode()) {
            return Collections.emptyList();
        }
        if (schedulesNode.isTextual()) {
            String text = schedulesNode.asText();
            if (!StringUtils.hasText(text)) {
                return Collections.emptyList();
            }
            JsonNode parsed = objectMapper.readTree(text);
            if (!parsed.isArray()) {
                log.warn("Dify validated_schedules_json string is not array: nodeType={}", parsed.getNodeType());
                return Collections.emptyList();
            }
            return objectMapper.readValue(parsed.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, AiGeneratePlanResult.ValidatedScheduleDTO.class));
        }
        if (schedulesNode.isArray()) {
            return objectMapper.readValue(schedulesNode.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, AiGeneratePlanResult.ValidatedScheduleDTO.class));
        }
        return Collections.emptyList();
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String stringify(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private List<DoctorSchedule> buildDoctorSchedules(Long departmentId,
                                                      List<AiGeneratePlanResult.ValidatedScheduleDTO> validatedSchedules,
                                                      List<EmployeeDTO> doctors,
                                                      Map<Long, RegistLevelDTO> registLevels) {
        Map<Long, EmployeeDTO> doctorMap = doctors.stream()
                .collect(Collectors.toMap(EmployeeDTO::getId, doctor -> doctor, (left, right) -> left));

        List<DoctorSchedule> schedules = new ArrayList<>();
        for (AiGeneratePlanResult.ValidatedScheduleDTO item : validatedSchedules) {
            if (item == null || item.getPhysicianId() == null || !StringUtils.hasText(item.getWorkDate()) || !StringUtils.hasText(item.getTimeSlot())) {
                continue;
            }
            EmployeeDTO doctor = doctorMap.get(item.getPhysicianId());
            if (doctor == null) {
                throw new RuntimeException("AI 返回了不存在的医生ID：" + item.getPhysicianId());
            }
            Long registLevelId = item.getRegistLevelId() != null ? item.getRegistLevelId() : doctor.getRegistLevelId();
            if (registLevelId == null) {
                throw new RuntimeException("医生缺少挂号级别，无法保存排班：" + doctor.getRealname());
            }
            RegistLevelDTO level = registLevels.get(registLevelId);
            if (level == null) {
                throw new RuntimeException("未找到挂号级别配置，ID=" + registLevelId);
            }

            DoctorSchedule schedule = new DoctorSchedule();
            schedule.setDepartmentId(departmentId);
            schedule.setPhysicianId(item.getPhysicianId());
            schedule.setWorkDate(LocalDate.parse(item.getWorkDate(), DATE_FORMATTER));
            schedule.setTimeSlot(normalizeTimeSlot(item.getTimeSlot()));
            schedule.setRegistLevelId(registLevelId);
            schedule.setTotalQuota(resolveQuota(item, level));
            schedule.setPrice(item.getPrice() != null ? item.getPrice() : defaultPrice(level.getPrice()));
            schedule.setAiSuggestion(item.getAiSuggestion());
            schedule.setStatus(StringUtils.hasText(item.getStatus()) ? item.getStatus() : "正常");
            schedules.add(schedule);
        }
        return schedules;
    }

    private String normalizeTimeSlot(String timeSlot) {
        String normalized = timeSlot.trim();
        if ("早上".equals(normalized)) {
            return "上午";
        }
        if ("中午".equals(normalized) || "晚上".equals(normalized)) {
            return "下午";
        }
        return normalized;
    }

    private Integer resolveQuota(AiGeneratePlanResult.ValidatedScheduleDTO item, RegistLevelDTO level) {
        if (item.getTotalQuota() != null && item.getTotalQuota() > 0) {
            return item.getTotalQuota();
        }
        if (item.getAvailableQuota() != null && item.getUsedQuota() != null) {
            return item.getAvailableQuota() + item.getUsedQuota();
        }
        if (level.getQuota() != null && level.getQuota() > 0) {
            return level.getQuota();
        }
        return 30;
    }

    private BigDecimal defaultPrice(BigDecimal price) {
        return price != null ? price : BigDecimal.ZERO;
    }

    private Integer resolveAiVersion(AiGeneratePlanResult result) {
        return result.getAiVersion() != null ? result.getAiVersion() : DEFAULT_AI_VERSION;
    }
}
