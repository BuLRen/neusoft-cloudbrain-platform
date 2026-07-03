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
import com.xikang.schedule.dto.DifyLeaveAdjustResult;
import com.xikang.schedule.entity.DoctorSchedule;
import com.xikang.schedule.entity.LeaveRequest;
import com.xikang.schedule.entity.SchedulePlan;
import com.xikang.schedule.mapper.DoctorScheduleMapper;
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
    private final DoctorScheduleMapper doctorScheduleMapper;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Value("${dify.api-key:}")
    private String difyApiKey;

    /** 替班工作流独立 API Key（在 Dify 创建新工作流后填入；为空时回退到 dify.api-key） */
    @Value("${dify.leave-adjust-api-key:}")
    private String difyLeaveAdjustApiKey;

    @Value("${dify.base-url:http://43.139.102.203}")
    private String difyBaseUrl;

    @Value("${dify.timeout-ms:660000}")
    private Long difyTimeoutMs;

    public DifyIntegrationService(RegistrationClient registrationClient,
                                   SchedulePlanService schedulePlanService,
                                   DoctorScheduleService doctorScheduleService,
                                   DoctorScheduleMapper doctorScheduleMapper,
                                   ObjectMapper objectMapper,
                                   WebClient.Builder webClientBuilder) {
        this.registrationClient = registrationClient;
        this.schedulePlanService = schedulePlanService;
        this.doctorScheduleService = doctorScheduleService;
        this.doctorScheduleMapper = doctorScheduleMapper;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void logDifyConfigurationOnStartup() {
        logDifyConfiguration("startup");
    }

    /**
     * 处理医生请假（调 Dify 7 节点工作流，生成 AI 替班方案）
     * <p>调用时机：管理员审批通过后，由 {@link LeaveRequestService#processLeave} 触发。
     * <p>失败时不抛异常（由上层 try/catch 降级），仅返回 null。
     *
     * @param leave    请假记录
     * @param schedule 被请假的排班（含 departmentId / usedQuota / totalQuota）
     * @return 解析后的替班方案 DTO；解析失败或工作流出错时返回 null
     */
    public DifyLeaveAdjustResult processLeaveWithAI(LeaveRequest leave, DoctorSchedule schedule) {
        if (leave == null || schedule == null) {
            log.warn("processLeaveWithAI 入参为空，跳过");
            return null;
        }
        if (!isConfigured()) {
            log.warn("Dify 未配置完整，跳过 AI 替班推荐");
            return null;
        }

        log.info("开始调用 Dify 替班工作流：leaveId={}, scheduleId={}", leave.getId(), schedule.getId());

        // 1. 后端预筛候选（防幻觉：LLM 只能从这批 ID 里选）
        List<SubstituteCandidate> candidates = findLeaveSubstitutes(schedule, leave);
        if (candidates.isEmpty()) {
            log.warn("未找到合格候选医生，跳过 AI 推荐：scheduleId={}", schedule.getId());
            DifyLeaveAdjustResult empty = new DifyLeaveAdjustResult();
            empty.setSource("no_candidate");
            empty.setRawJson("{\"error\":\"no candidate available\"}");
            return empty;
        }

        // 2. 构造 Dify 开始节点 inputs（全部 string）
        Map<String, Object> workflowInput = buildLeaveWorkflowInput(leave, schedule, candidates);

        // 3. 调 Dify 工作流（blocking 模式）
        String rawResponse;
        try {
            rawResponse = callLeaveAdjustWorkflow(workflowInput);
        } catch (Exception ex) {
            log.error("Dify 替班工作流调用失败：leaveId={}, err={}", leave.getId(), ex.getMessage());
            return null;
        }

        // 4. 解析 + 守门员校验
        return parseLeaveAdjustResult(rawResponse, candidates);
    }

    /**
     * 后端预筛替班候选（SQL + Java 双重过滤）
     * <p>规则：同科室 + 当天在岗 + 状态正常 + 有余号 + 排除请假医生本人。
     */
    private List<SubstituteCandidate> findLeaveSubstitutes(DoctorSchedule schedule, LeaveRequest leave) {
        try {
            List<DoctorSchedule> available = doctorScheduleMapper.selectAvailable(
                    schedule.getDepartmentId(), leave.getLeaveDate());
            return available.stream()
                    .filter(s -> !s.getPhysicianId().equals(leave.getPhysicianId()))
                    .filter(s -> "正常".equals(s.getStatus()))
                    .filter(s -> s.getAvailableQuota() != null && s.getAvailableQuota() > 0)
                    .map(s -> {
                        SubstituteCandidate c = new SubstituteCandidate();
                        c.setPhysicianId(s.getPhysicianId());
                        c.setWeeklyLoad(0); // 简化：当前只用可用号源判断
                        c.setAvailableSlots(s.getTimeSlot() != null ? s.getTimeSlot() : "全天空闲");
                        return c;
                    })
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("查询替班候选失败：{}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构造 Dify 开始节点输入（全部 string，规避 Dify 类型限制）
     */
    private Map<String, Object> buildLeaveWorkflowInput(LeaveRequest leave,
                                                         DoctorSchedule schedule,
                                                         List<SubstituteCandidate> candidates) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("leave_id", String.valueOf(leave.getId()));
        input.put("leave_summary", String.format("%s %s 科室%d 医生%d 因%s 请%s",
                leave.getLeaveDate(),
                leave.getTimeSlot() != null ? leave.getTimeSlot() : "全天",
                schedule.getDepartmentId(),
                leave.getPhysicianId(),
                StringUtils.hasText(leave.getReason()) ? leave.getReason() : "请假",
                leave.getLeaveType() != null ? leave.getLeaveType() : "事假"));
        input.put("candidates_brief", candidates.stream()
                .map(c -> String.format("医生%d(%d,本周%d班,%s)",
                        c.getPhysicianId(), c.getPhysicianId(), c.getWeeklyLoad(), c.getAvailableSlots()))
                .collect(Collectors.joining("|")));
        input.put("affected_count", String.valueOf(schedule.getUsedQuota() != null ? schedule.getUsedQuota() : 0));
        return input;
    }

    /**
     * 调用 Dify 替班工作流（blocking 模式）。
     * 与 {@link #callDifyWorkflow} 相似，但使用独立的 API Key 配置（便于区分两个工作流）。
     */
    private String callLeaveAdjustWorkflow(Map<String, Object> workflowInput) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("inputs", workflowInput);
        requestBody.put("response_mode", "blocking");
        requestBody.put("user", "schedule-service-leave");

        try {
            log.info("Dify leave-adjust request: {}", objectMapper.writeValueAsString(requestBody));
        } catch (JsonProcessingException ignored) {
        }

        // 复用 dify.base-url；API Key 优先用 leave-adjust 专用，否则回退到默认
        String apiKey = StringUtils.hasText(difyLeaveAdjustApiKey) ? difyLeaveAdjustApiKey : difyApiKey;

        WebClient client = webClientBuilder
                .baseUrl(trimTrailingSlash(difyBaseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        return client.post()
                .uri("/v1/workflows/run")
                .bodyValue(requestBody)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("[EMPTY BODY]")
                        .map(body -> {
                            if (response.statusCode().isError()) {
                                log.error("Dify leave-adjust HTTP {} - body: {}",
                                        response.statusCode(), body);
                                throw new RuntimeException("Dify 替班工作流 HTTP "
                                        + response.statusCode() + "：" + body);
                            }
                            return body;
                        }))
                .timeout(java.time.Duration.ofMillis(difyTimeoutMs))
                .block();
    }

    /**
     * 解析 Dify 替班工作流返回 + 守门员校验
     * <p>双重防幻觉：① 用正则提取 JSON 块；② 校验 substitute_id 在候选清单内。
     */
    private DifyLeaveAdjustResult parseLeaveAdjustResult(String rawResponse,
                                                          List<SubstituteCandidate> candidates) {
        DifyLeaveAdjustResult result = new DifyLeaveAdjustResult();
        if (!StringUtils.hasText(rawResponse)) {
            result.setSource("error");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            if (root.has("error")) {
                log.error("Dify 替班工作流错误：{}", root.path("error").asText());
                result.setSource("error");
                return result;
            }

            JsonNode outputs = root.path("data").path("outputs");
            // outputs 可能整体是 string（Dify 兼容模式）
            if (outputs.isTextual() && looksLikeJson(outputs.asText())) {
                outputs = objectMapper.readTree(outputs.asText());
            }

            // 从 outputs 里取 result 字段（JSON-in-String）
            String jsonStr = outputs.path("result").asText("");
            if (!StringUtils.hasText(jsonStr)) {
                // 兼容：直接整个 outputs 当 JSON
                jsonStr = outputs.toString();
            }

            // 正则提取 {...}（防止 LLM 输出带 markdown 代码块）
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\{[^{}]*\\}", java.util.regex.Pattern.DOTALL)
                    .matcher(jsonStr);
            String cleanJson = m.find() ? m.group() : jsonStr;

            JsonNode payload = objectMapper.readTree(cleanJson);

            // 守门员校验：substitute_id 必须在候选清单内
            String substituteId = payload.path("substitute_id").asText("");
            boolean valid = candidates.stream()
                    .anyMatch(c -> String.valueOf(c.getPhysicianId()).equals(substituteId));

            if (!valid) {
                // 降级：取候选清单第一个
                SubstituteCandidate fallback = candidates.get(0);
                log.warn("Dify 返回的 substitute_id={} 不在候选清单，降级到首位 {}",
                        substituteId, fallback.getPhysicianId());
                result.setSubstitutePhysicianId(fallback.getPhysicianId());
                result.setSubstitutePhysicianName("候选医生" + fallback.getPhysicianId());
                result.setReason("AI 推荐 ID 非法，降级到候选首位");
                result.setSource("fallback");
            } else {
                result.setSubstitutePhysicianId(Long.valueOf(substituteId));
                result.setSubstitutePhysicianName(payload.path("substitute_name").asText(""));
                result.setAdjustType(payload.path("adjust_type").asText("临时替班"));
                result.setReason(payload.path("reason").asText(""));
                result.setPatientNotification(payload.path("patient_notification").asText(""));
                result.setSource(payload.path("source").asText("ai"));
            }
            result.setRawJson(cleanJson);
            return result;

        } catch (Exception e) {
            log.error("解析 Dify 替班结果失败：{}", e.getMessage(), e);
            result.setSource("error");
            return result;
        }
    }

    /** 替班候选内部 DTO */
    private static class SubstituteCandidate {
        private Long physicianId;
        private int weeklyLoad;
        private String availableSlots;

        public Long getPhysicianId() { return physicianId; }
        public void setPhysicianId(Long physicianId) { this.physicianId = physicianId; }
        public int getWeeklyLoad() { return weeklyLoad; }
        public void setWeeklyLoad(int weeklyLoad) { this.weeklyLoad = weeklyLoad; }
        public String getAvailableSlots() { return availableSlots; }
        public void setAvailableSlots(String availableSlots) { this.availableSlots = availableSlots; }
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
