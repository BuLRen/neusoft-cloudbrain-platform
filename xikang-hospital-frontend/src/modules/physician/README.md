# 门诊诊疗（人员 A）

## 启动

1. PostgreSQL：`cd xikang-cloud-hospital/docker && docker compose up -d`
2. 后端：`cd xikang-cloud-hospital && mvn -pl physician-service -am install -DskipTests && cd physician-service && mvn spring-boot:run`
3. 前端：`npm run dev`（`/api/physician` 已代理到 `8092`，绕过网关鉴权）

## 页面（门诊诊疗 · 6 步）

- 第 1 步：`/physician/queue` 待诊接诊
- 第 2 步：`/physician/record` 病历与初步诊断
- 第 3 步：`/physician/orders` 开立检查检验（W2）
- 第 4 步：`/physician/results` 查看结果（W3）
- 第 5 步：`/physician/diagnosis` 门诊确诊（W4）
- 第 6 步：`/physician/prescription` 开立处方

## AI 流水线（v3）

| 步骤 | API | 说明 |
|------|-----|------|
| 初步诊断 | `POST /api/physician/ai/preliminary-diagnosis` | Dify Workflow：`text` + `preHandle` |
| W1 | `POST /api/physician/ai/w1/structure` | 长文本/表单 → 标准病历字段 |
| W2 | `POST /api/physician/ai/w2/recommend` | 初步判断 + 检查推荐 |
| W2b | `POST /api/physician/ai/w2b/simulate` | 常规检验模拟 + CT CNN |
| W3 | `POST /api/physician/ai/w3/analyze` | 结果解读（非最终诊断） |
| W3 状态 | `GET /api/physician/ai/w3/status?registerId=` | 查询已持久化的 W3 解读 |
| W3 异步触发 | `POST /api/physician/ai/w3/trigger-async` | 医技结果提交后异步触发（内部） |
| W4 | `POST /api/physician/ai/w4/diagnose` | 诊断与概率（依赖 W3 输出） |
| 一键 | `POST /api/physician/ai/pipeline/run` | 串联 W1–W4 |

未配置 Dify 时使用内置 `FallbackWorkflowEngine`。

## Dify 初步诊断工作流

Dify 官方接口：`POST {DIFY_BASE_URL}/v1/workflows/run`（`response_mode: blocking`）。  
**每个 Workflow App 只有一个 API Key**（`app-xxx`），放在请求头 `Authorization: Bearer {api_key}`；URL 和 body 里**没有** workflow id / 第二个 key。

本项目配置对应关系：

| 配置项 | 填什么 | 不是什么 |
|--------|--------|----------|
| `api-key` / `DIFY_API_KEY` | Dify 控制台「访问 API」里的 `app-xxx` | — |
| `workflow-preliminary` / `DIFY_WORKFLOW_PRELIMINARY` | `true`（开关，非空即启用初步诊断 Dify） | 不是 `app-xxx`，不是 workflow UUID |

请求体仅传：

```json
{
  "inputs": {
    "text": "患者或病历文本",
    "preHandle": true,
    "model": "deepseek-v4-flash"
  },
  "response_mode": "blocking",
  "user": "physician-reg-{registerId}"
}
```

### 环境变量

| 变量 | 说明 |
|------|------|
| `DIFY_BASE_URL` | 本地/默认 Dify 根地址（W1–W4 等），如 `http://localhost`（勿重复 `/v1`） |
| `DIFY_BASE_URL_PRELIMINARY` | 初步诊断专用 Dify 根地址，如 `https://api.dify.ai`；为空时回退 `DIFY_BASE_URL` |
| `DIFY_API_KEY` | **必填**。初步诊断 Workflow App 的 API Key（`app-xxx`），与 curl 文档 `Bearer` 相同 |
| `DIFY_WORKFLOW_PRELIMINARY` | 填 `true` 启用初步诊断；**不要**把 `app-xxx` 写在这里 |
| `DIFY_PRELIM_OUTPUT_ROOT` | 结构化输出根键，默认 `output_structured` |
| `DIFY_PRELIM_OUTPUT_DIAGNOSIS_TEXT` | 长文推理，默认映射 `answer` |
| `DIFY_PRELIM_OUTPUT_SUGGESTED_DISEASES` | 疾病列表，默认映射 `diseaseDetail` |

`output_structured` 内字段与前端一致：`clinicalSummary`、`primaryDiagnosis`、`answer`（长文仅存档）、`diseaseDetail[]`、`redFlags`、`excludedDiagnoses`、`confidence`、`isRecalled`（boolean）、`knowledgeBaseRecall`（知识库召回原文，展示在「技术与审计信息」）。

`application.yml` 中设置 `xikang.ai.dify.enabled=true` 并填入上述变量。

### 联调步骤

1. 在 Dify 发布工作流，确认输入变量为 `text`、`preHandle`。
2. 记录「结束」节点输出变量名，写入 `preliminary-output-keys` 或环境变量。
3. 重启 `physician-service`，前端第 2 步 →「生成初步诊断」。
4. 失败时响应为通用错误文案；服务端日志含 `workflow_run_id`，不含患者全文。

### Dify W2（开立检查检验推荐）

与初步诊断类似：`POST /v1/workflows/run`，`response_mode: blocking`。

| 配置项 | 说明 |
|--------|------|
| `DIFY_WORKFLOW_W2` | 填 `true` 启用 W2 Dify；**不要**填 `app-xxx` |
| `DIFY_API_KEY_W2` | **推荐**。W2 Workflow App 的 API Key（与开始节点变量名无关） |
| `DIFY_W2_OUTPUT_ROOT` | 可选。结束节点若用根变量包裹 JSON，填该变量名 |

**Dify 开始节点变量（String）：**

- `clinical_context_json` — 后端由病历 + 初步诊断 meta + 预问诊组装
- `available_examinations_json` — 后端 `medical_technology` 可开项目列表

**结束节点输出（与前端 `W2Output` 对齐）：** `preliminaryAssessment`、`recommendedExaminations[]`（含 `techCode`，后端映射 `techId`）、`notRecommendedNote`、`unmatchedSuggestions`。

未配置 W2 或调用失败时走内置 `FallbackWorkflowEngine`。

### Dify W3（查看结果 / 检查检验结果解读）

与 W2 类似：`POST {DIFY_BASE_URL}/v1/workflows/run`，`response_mode: blocking`，独立 API Key。

| 配置项 | 说明 |
|--------|------|
| `DIFY_WORKFLOW_W3` | 填 `true` 启用 W3 Dify；**不要**填 `app-xxx` |
| `DIFY_API_KEY_W3` | **必填（启用 W3 时）**。W3 Workflow App 的 API Key |
| `DIFY_BASE_URL` | 自托管 Dify 根地址，如 `http://43.139.102.203`（勿带 `/v1`） |

**Dify 开始节点变量（String）：**

- `registerId`
- `structuredRecordJson` — 后端 `loadStructuredRecord()` 序列化
- `allResultsJson` — 后端检查 + 检验 `resultText` 列表序列化
- `preliminaryAssessment` — W2 初步判断（优先进程内缓存，回退病历/推荐理由）

**结束节点输出（与前端 `W3Output` 对齐）：** `registerId`、`examSummaries[]`、`overallAnalysis`、`explicitNonDiagnosis`。

前端第 4 步「运行 W3」→ `POST /api/physician/ai/w3/analyze`；结果持久化后可通过 `GET /api/physician/ai/w3/status` 查询。

未配置 W3 或 Key 为空时走内置 `FallbackWorkflowEngine`。

### 其它 Dify（W1、W4）

- `DIFY_WORKFLOW_W1` / `W4`：非空时尝试旧版 `invokeWorkflow`（共用 `api-key` 时易命中错误 App，慎用）。
- `CT_INFERENCE_URL`：外部 CT 推理服务，未配置则用内置模拟。
