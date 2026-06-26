# Coze → Dify 迁移改动清单（schedule-service）

> 范围：把 schedule-service 里所有 Coze 调用改成 Dify（`POST /v1/workflows/run`，blocking 模式），复合类型字段以 String(JSON) 形式传入。

---

## 一、协议差异速查

| 维度 | Coze（现状） | Dify（目标） |
|---|---|---|
| Base URL | `https://api.coze.cn` | `http://43.139.102.203`（环境变量配） |
| 路径 | `/v1/workflow/stream_run` | `/v1/workflows/run` |
| Auth | `Bearer {COZE_API_KEY}` | `Bearer app-MsO0x12C3vA9bvUt7sEh6wAC` |
| 请求体外层 | `{workflow_id, parameters, app_id}` | `{inputs, response_mode, user}` |
| inputs 字段命名 | `physicians` / `weekday_patterns` / `holidays`（嵌套对象） | `physicians_json` / `weekday_patterns_json` / `holidays_json`（**String 化**） |
| 响应模式 | SSE 流（`event:` + `data:`） | blocking（一次性 JSON） |
| 响应体取值 | 解析 SSE 最后一个事件 | `data.outputs.{validated_schedules, statistics, errors, warnings, message}` |
| 工作流标识 | 显式传 `workflow_id` | 隐式（API key 里已绑定） |

---

## 二、改动清单（7 个文件）

### 1. `application.yml`（schedule-service/src/main/resources/）

**删除**：
```yaml
coze:
  api-key: ${COZE_API_KEY:}
  workflow-id: ${COZE_WORKFLOW_ID:}
  api-url: ${COZE_API_URL:https://api.coze.cn}
  timeout-ms: ${COZE_TIMEOUT_MS:300000}
```

**新增**：
```yaml
dify:
  api-key: ${DIFY_API_KEY:}                # 必填，格式 app-xxxx
  base-url: ${DIFY_BASE_URL:http://43.139.102.203}
  timeout-ms: ${DIFY_TIMEOUT_MS:300000}    # blocking 模式下要够长，建议 5 分钟
```

> 注：Dify 不需要 workflow-id（API key 已绑定工作流）。

### 2. `bootstrap.yml`（同目录）

同步把 `coze:` 段改成 `dify:` 段（Nacos 配置加载的默认值）。

### 3. 新建 `DifyIntegrationService.java`（替代 `CozeIntegrationService.java`）

**包路径不变**：`com.xikang.schedule.service`

**类签名**：
```java
@Slf4j
@Service
public class DifyIntegrationService {
    @Value("${dify.api-key:}")        private String difyApiKey;
    @Value("${dify.base-url:http://43.139.102.203}")  private String difyBaseUrl;
    @Value("${dify.timeout-ms:300000}") private Long difyTimeoutMs;
    // ...
}
```

**核心方法签名（保持和旧类一致，调用方少改）**：
- `public AiGeneratePlanResult orchestrate(AiGeneratePlanRequest, Consumer<StageProgress>)` — 不变
- `@Transactional public AiGeneratePlanResult persistAiPlanAndSchedules(...)` — 不变
- `public record StageProgress(String stage, int percent, String message)` — 不变
- `public boolean isConfigured()` — 不变

**关键内部改动**：

#### 3.1 `buildWorkflowInput` — 字段名加 `_json`，复合类型 String 化

```java
private Map<String, Object> buildWorkflowInput(Long departmentId, DepartmentDTO department,
                                                String month, List<EmployeeDTO> doctors) {
    ObjectMapper om = objectMapper;
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("department_id", departmentId);
    input.put("department_name", department.getName());
    input.put("month", month);
    // ⚠️ Dify 开始节点不支持 Array/Object，必须 JSON.stringify 成 String
    input.put("physicians_json", toJsonString(om, doctors.stream().map(this::toPhysicianPayload).collect(Collectors.toList())));
    input.put("weekday_patterns_json", toJsonString(om, buildWeekdayPatterns(month, doctors)));
    input.put("holidays_json", toJsonString(om, buildHolidays(month)));
    return input;
}

private String toJsonString(ObjectMapper om, Object obj) {
    try {
        return om.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("序列化失败: " + e.getMessage(), e);
    }
}
```

#### 3.2 `callDifyWorkflow`（替代 `callCozeWorkflow`） — blocking 模式，删 SSE 解析

```java
private String callDifyWorkflow(Map<String, Object> workflowInput,
                                 Consumer<StageProgress> progressSink) {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("inputs", workflowInput);          // ← Dify 用 inputs 包裹
    requestBody.put("response_mode", "blocking");      // ← blocking 模式
    requestBody.put("user", "schedule-service");

    WebClient client = webClientBuilder
            .baseUrl(trimTrailingSlash(difyBaseUrl))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + difyApiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    // blocking 模式下一次性返回，不再用 bodyToFlux
    String raw = client.post()
            .uri("/v1/workflows/run")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(java.time.Duration.ofMillis(difyTimeoutMs))
            .block();
    // 推进进度到 85%（Coze 时代是流式累加，blocking 一步到位）
    emitStage(progressSink, "calling_dify", 85, "Dify 工作流执行完成");
    return raw;
}
```

> 比旧实现**减少约 40 行**（删了 chunkCount、EXPECTED_COZE_EVENTS、bodyToFlux 等）。

#### 3.3 `parseWorkflowResult` — 简化，只取 `data.outputs`

```java
private AiGeneratePlanResult parseWorkflowResult(String rawResponse) {
    if (!StringUtils.hasText(rawResponse)) {
        throw new RuntimeException("Dify 返回为空");
    }
    try {
        JsonNode root = objectMapper.readTree(rawResponse);

        // 错误检查
        if (root.has("error")) {
            String errMsg = root.path("error").asText("Dify 返回错误");
            throw new RuntimeException("Dify 调用失败：" + errMsg);
        }

        // Dify blocking 响应：{ data: { outputs: {...}, status: "succeeded" } }
        JsonNode dataNode = root.path("data");
        String status = dataNode.path("status").asText("");
        if (!"succeeded".equalsIgnoreCase(status)) {
            throw new RuntimeException("Dify 工作流未成功，status=" + status
                    + ", error=" + dataNode.path("error").asText(""));
        }

        JsonNode outputs = dataNode.path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            throw new RuntimeException("Dify 返回中缺少 outputs 字段");
        }

        // outputs 里的 validated_schedules 可能是数组，也可能是字符串（兼容 Dify 把它字符串化的情况）
        return parseFromPayload(outputs);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("解析 Dify 返回结果失败", e);
    }
}
```

> 删掉 100+ 行的 SSE 流式解析（`STREAM_EVENT_PREFIX` / `parseJsonObjectStreamPayload` / `pickPayloadCandidate` / `extractPayloadNode` 等）。`parseFromPayload` 和 `parseValidatedSchedules` 保留（Dify 也可能把 `validated_schedules` 字符串化，复用容错逻辑）。

#### 3.4 进度阶段标识

把 `calling_coze` 改成 `calling_dify`（影响前端枚举）。

### 4. 删除 `CozeIntegrationService.java`

整个文件删除，由 `DifyIntegrationService.java` 替代。

### 5. `AiGenerateTaskService.java`

```java
// 旧
private final CozeIntegrationService cozeIntegrationService;
public AiGenerateTaskService(CozeIntegrationService cozeIntegrationService) { ... }
AiGeneratePlanResult result = cozeIntegrationService.orchestrate(request, sink);
cozeIntegrationService.persistAiPlanAndSchedules(request, result, sink);
Consumer<CozeIntegrationService.StageProgress> sink = ...

// 新
private final DifyIntegrationService difyIntegrationService;
public AiGenerateTaskService(DifyIntegrationService difyIntegrationService) { ... }
AiGeneratePlanResult result = difyIntegrationService.orchestrate(request, sink);
difyIntegrationService.persistAiPlanAndSchedules(request, result, sink);
Consumer<DifyIntegrationService.StageProgress> sink = ...
```

注释里的 "Coze" 字样全部替换成 "Dify"。

### 6. `LeaveRequestService.java`

把 `cozeIntegrationService` 字段和 `processLeaveWithAI` 调用同步改成 `difyIntegrationService`（行为不变，只是改名）。注释里"调用 Coze 生成调整方案"等改成"Dify"。

### 7. `CozeWebhookController.java` → 重命名为 `DifyWebhookController.java`

类名改、`@RequestMapping` 路径**保持不变**（`/api/schedule/webhook`，避免破坏前端），方法注释里的"Coze"改"Dify"。

### 8. `schedule.ts`（前端）

```typescript
// 旧
export type AiTaskStage =
  | 'validating'
  | 'loading_doctors'
  | 'calling_coze'    // ← 改
  | 'parsing_ai'
  ...

// 新
export type AiTaskStage =
  | 'validating'
  | 'loading_doctors'
  | 'calling_dify'    // ← 改
  | 'parsing_ai'
  ...
```

> 仅这一个枚举值，不影响其他前端代码（除非有地方做了字符串相等性比较，需要同步改，会全局搜一遍）。

---

## 三、改动前后请求体对比（验证用）

### Java 端发给 Dify 的 payload（最终形态）

```json
POST http://43.139.102.203/v1/workflows/run
Authorization: Bearer app-MsO0x12C3vA9bvUt7sEh6wAC
Content-Type: application/json

{
  "inputs": {
    "department_id": 1,
    "department_name": "内科",
    "month": "2026-07",
    "physicians_json": "[{\"id\":5,\"realname\":\"内科刘主任\",\"position\":\"主任医师号\"}]",
    "weekday_patterns_json": "{\"周一\":{\"avg_quota\":150,\"usage_rate\":0.85}}",
    "holidays_json": "[\"2026-07-05\"]"
  },
  "response_mode": "blocking",
  "user": "schedule-service"
}
```

✅ 符合 WF-01 文档里开始节点定义
✅ 符合你给的 curl 示例结构
✅ 复合类型全部 String 化

---

## 四、不动的部分（明确边界）

- `AiGeneratePlanResult.java` / `AiGeneratePlanRequest.java` / `ValidatedScheduleDTO` — DTO 不变
- `ScheduleController.java` / `SchedulePlanService.java` / `DoctorScheduleService.java` — 业务层不变
- `RegistrationClient.java` — Feign 调用不变
- 数据库表结构 — 不变
- 前端除 `schedule.ts` 枚举外其他文件 — 待全局搜确认（应该只有这一处）

---

## 五、执行顺序（落地步骤）

1. 改 `application.yml` + `bootstrap.yml`（新增 dify 配置）
2. 新建 `DifyIntegrationService.java`（从旧类复制 + 改协议）
3. 改 `AiGenerateTaskService.java`（改注入）
4. 改 `LeaveRequestService.java`（改注入）
5. 重命名 `CozeWebhookController.java` → `DifyWebhookController.java`
6. 删除 `CozeIntegrationService.java`
7. 改前端 `schedule.ts` 枚举值
8. 编译验证：`mvn compile -pl schedule-service -am`

---

## 六、环境变量（你需要做的）

部署/启动前，在 schedule-service 的 `.env` 或环境变量里加：

```bash
DIFY_API_KEY=app-MsO0x12C3vA9bvUt7sEh6wAC
DIFY_BASE_URL=http://43.139.102.203
DIFY_TIMEOUT_MS=300000
```

旧的 `COZE_API_KEY / COZE_WORKFLOW_ID / COZE_API_URL` 可以删掉。
