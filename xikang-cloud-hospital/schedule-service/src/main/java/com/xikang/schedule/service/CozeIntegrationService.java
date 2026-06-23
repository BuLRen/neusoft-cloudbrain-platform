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
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Coze 集成服务
 */
@Slf4j
@Service
public class CozeIntegrationService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String DEFAULT_GENERATE_TYPE = "full";
    private static final int DEFAULT_AI_VERSION = 1;
    private static final String STREAM_EVENT_PREFIX = "data:";

    /** Coze 调用阶段进度区间下限（含），到达后由 chunk 计数在线性推到上限 */
    private static final int COZE_PHASE_PERCENT_MIN = 30;
    private static final int COZE_PHASE_PERCENT_MAX = 85;
    /** 经验值：Coze 流式返回的非 PING event 大致数量，用于把 chunk 数换算成 0~1 进度 */
    private static final int EXPECTED_COZE_EVENTS = 6;

    private final RegistrationClient registrationClient;
    private final SchedulePlanService schedulePlanService;
    private final DoctorScheduleService doctorScheduleService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${coze.api-key:}")
    private String cozeApiKey;

    @Value("${coze.workflow-id:}")
    private String cozeWorkflowId;

    @Value("${coze.api-url:https://api.coze.cn}")
    private String cozeApiUrl;

    @Value("${coze.timeout-ms:130000}")
    private Long cozeTimeoutMs;

    public CozeIntegrationService(RegistrationClient registrationClient,
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
    public void logCozeConfigurationOnStartup() {
        logCozeConfiguration("startup");
    }

    /**
     * 处理医生请假（AI 理解 + 生成调整方案）
     */
    public void processLeaveWithAI(LeaveRequest leaveRequest) {
        log.info("【预留】Coze AI 处理请假：leaveId={}, physicianId={}",
                leaveRequest.getId(), leaveRequest.getPhysicianId());
    }

    /**
     * 分析号源分布
     */
    public void analyzeQuotaDistribution(Long departmentId, int daysAhead) {
        log.info("【预留】Coze AI 分析号源分布：departmentId={}, days={}", departmentId, daysAhead);
    }

    /**
     * 理解自然语言请假请求
     */
    public String parseLeaveRequest(String rawText) {
        log.info("【预留】Coze AI 理解自然语言请假：{}", rawText);
        return "{}";
    }

    /**
     * 获取 Coze API 配置状态
     */
    public boolean isConfigured() {
        return StringUtils.hasText(cozeApiKey)
                && StringUtils.hasText(cozeWorkflowId)
                && StringUtils.hasText(cozeApiUrl);
    }

    private void logCozeConfiguration(String stage) {
        log.info("Coze config [{}]: apiKeyPresent={}, workflowIdPresent={}, apiUrl='{}', timeoutMs={}",
                stage,
                StringUtils.hasText(cozeApiKey),
                StringUtils.hasText(cozeWorkflowId),
                cozeApiUrl,
                cozeTimeoutMs);
    }

    // ==================== 异步编排入口（AiGenerateTaskService 调用） ====================

    /**
     * 编排阶段：参数校验 → 拉科室医生 → 调 Coze → 解析。
     * <p>无 @Transactional，由 AiGenerateTaskService 在异步线程里调用。
     * 落库动作通过 {@link #persistAiPlanAndSchedules} 单独事务完成。</p>
     */
    public AiGeneratePlanResult orchestrate(AiGeneratePlanRequest request,
                                            Consumer<StageProgress> progressSink) {
        emitStage(progressSink, "validating", 5, "参数校验");
        validateGenerateRequest(request);

        if (!isConfigured()) {
            logCozeConfiguration("orchestrate");
            throw new RuntimeException("Coze 工作流配置不完整，请检查 schedule-service 的 coze 配置");
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

        emitStage(progressSink, "calling_coze", COZE_PHASE_PERCENT_MIN, "调用 Coze 工作流");
        String rawResponse = callCozeWorkflow(workflowInput, progressSink);
        log.info("Coze raw response [len={}]", rawResponse == null ? 0 : rawResponse.length());

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

    private Map<String, Object> buildWorkflowInput(Long departmentId,
                                                   DepartmentDTO department,
                                                   String month,
                                                   List<EmployeeDTO> doctors) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("department_id", departmentId);
        input.put("department_name", department.getName());
        input.put("month", month);
        input.put("physicians", doctors.stream().map(this::toPhysicianPayload).collect(Collectors.toList()));
        input.put("weekday_patterns", buildWeekdayPatterns(month, doctors));
        input.put("holidays", buildHolidays(month));
        return input;
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
     * 调用 Coze stream_run 工作流。
     * 改为 bodyToFlux 累积，每收到一个 chunk 推进 Coze 阶段进度。
     * <p>Coze SSE 块可能是 text/event-stream 增量到 String 的 bodyToFlux&lt;String&gt;（整段）
     * 或 bodyToFlux&lt;DataBuffer&gt;（分段）。本实现按"完整响应字符串"取：WebClient 串行追加。</p>
     */
    private String callCozeWorkflow(Map<String, Object> workflowInput,
                                    Consumer<StageProgress> progressSink) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("workflow_id", cozeWorkflowId);
        requestBody.put("parameters", workflowInput);
        requestBody.put("app_id", "schedule-service");

        WebClient client = webClientBuilder
                .baseUrl(trimTrailingSlash(cozeApiUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cozeApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();

        StringBuilder buffer = new StringBuilder();
        AtomicLong chunkCount = new AtomicLong(0);

        return client.post()
                .uri("/v1/workflow/stream_run")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(java.time.Duration.ofMillis(cozeTimeoutMs))
                .doOnNext(chunk -> {
                    buffer.append(chunk);
                    long n = chunkCount.incrementAndGet();
                    // 把 chunk 数换算到 30~85 之间
                    double ratio = Math.min(1.0, (double) n / EXPECTED_COZE_EVENTS);
                    int percent = COZE_PHASE_PERCENT_MIN
                            + (int) Math.round((COZE_PHASE_PERCENT_MAX - COZE_PHASE_PERCENT_MIN) * ratio);
                    if (progressSink != null) {
                        try {
                            progressSink.accept(new StageProgress("calling_coze", percent,
                                    "调用 Coze 工作流（已收到 " + n + " 个数据块）"));
                        } catch (RuntimeException ignored) {
                        }
                    }
                })
                .collectList()
                .blockOptional()
                .map(chunks -> buffer.toString())
                .orElseThrow(() -> new RuntimeException("Coze 工作流未返回结果"));
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private AiGeneratePlanResult parseWorkflowResult(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new RuntimeException("Coze 返回为空");
        }

        String trimmed = rawResponse.trim();
        if (!trimmed.startsWith("id:") && !trimmed.startsWith("data:")) {
            try {
                JsonNode payload = parseJsonObjectStreamPayload(trimmed);
                if (payload == null) {
                    throw new RuntimeException("Coze 返回中未找到有效结果");
                }
                return parseFromPayload(payload);
            } catch (java.io.IOException exception) {
                throw new RuntimeException("解析 Coze 返回结果失败", exception);
            }
        }

        JsonNode finalEvent = null;
        String currentEvent = null;
        StringBuilder dataBuffer = new StringBuilder();
        for (String rawLine : trimmed.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (dataBuffer.length() > 0) {
                    JsonNode eventNode = tryParseJson(dataBuffer.toString());
                    JsonNode candidate = pickPayloadCandidate(eventNode, currentEvent);
                    if (candidate != null) {
                        finalEvent = candidate;
                    }
                }
                currentEvent = null;
                dataBuffer.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
                continue;
            }
            if (line.startsWith(STREAM_EVENT_PREFIX)) {
                if (dataBuffer.length() > 0) {
                    dataBuffer.append('\n');
                }
                dataBuffer.append(line.substring(STREAM_EVENT_PREFIX.length()).trim());
                continue;
            }
        }
        if (dataBuffer.length() > 0) {
            JsonNode eventNode = tryParseJson(dataBuffer.toString());
            JsonNode candidate = pickPayloadCandidate(eventNode, currentEvent);
            if (candidate != null) {
                finalEvent = candidate;
            }
        }

        if (finalEvent == null) {
            throw new RuntimeException("Coze 流式返回中未找到有效结果");
        }
        return parseFromPayload(finalEvent);
    }

    private JsonNode parseJsonObjectStreamPayload(String text) throws java.io.IOException {
        JsonNode finalPayload = null;
        com.fasterxml.jackson.databind.MappingIterator<JsonNode> iterator = objectMapper
                .readerFor(JsonNode.class)
                .readValues(text);
        while (iterator.hasNextValue()) {
            JsonNode eventNode = iterator.nextValue();
            JsonNode candidate = extractPayloadNode(eventNode);
            if (candidate != null) {
                finalPayload = candidate;
            }
        }
        return finalPayload;
    }

    private JsonNode tryParseJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException exception) {
            log.debug("跳过无法解析的 Coze 流片段: {}", text.substring(0, Math.min(text.length(), 120)));
            return null;
        }
    }

    private JsonNode pickPayloadCandidate(JsonNode eventNode, String eventName) {
        if (eventNode == null) {
            return null;
        }
        if (eventName != null && !"Message".equalsIgnoreCase(eventName)) {
            return null;
        }
        return extractPayloadNode(eventNode);
    }

    private JsonNode extractPayloadNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.has("validated_schedules")) {
            return node;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (!StringUtils.hasText(text) || !looksLikeJson(text)) {
                return null;
            }
            try {
                return extractPayloadNode(objectMapper.readTree(text));
            } catch (JsonProcessingException exception) {
                return null;
            }
        }
        JsonNode content = node.get("content");
        if (content != null) {
            JsonNode inner = extractPayloadNode(content);
            if (inner != null) {
                return inner;
            }
        }
        if (node.isObject()) {
            for (Entry<String, JsonNode> entry : iterable(node.fields())) {
                JsonNode candidate = extractPayloadNode(entry.getValue());
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private AiGeneratePlanResult parseFromPayload(JsonNode payloadNode) {
        try {
            AiGeneratePlanResult result = objectMapper.treeToValue(payloadNode, AiGeneratePlanResult.class);
            if (result == null) {
                throw new RuntimeException("Coze 返回为空");
            }
            if (result.getValidatedSchedules() == null || result.getValidatedSchedules().isEmpty()) {
                result.setValidatedSchedules(parseValidatedSchedules(payloadNode));
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
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("解析 Coze 返回结果失败", exception);
        }
    }

    private List<AiGeneratePlanResult.ValidatedScheduleDTO> parseValidatedSchedules(JsonNode payloadNode) throws JsonProcessingException {
        JsonNode schedulesNode = payloadNode == null ? null : payloadNode.get("validated_schedules");
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
                log.warn("Coze validated_schedules string is not array: nodeType={}", parsed.getNodeType());
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

    private <T> Iterable<T> iterable(java.util.Iterator<T> iterator) {
        return () -> iterator;
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String stringify(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
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
