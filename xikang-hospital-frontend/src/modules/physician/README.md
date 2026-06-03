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
| W3 | `POST /api/physician/ai/w3/analyze` | 结果整理（非最终诊断） |
| W4 | `POST /api/physician/ai/w4/diagnose` | 诊断与概率 |
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
| `DIFY_BASE_URL` | 如 `https://api.dify.ai` 或自托管根地址（勿重复 `/v1`） |
| `DIFY_API_KEY` | **必填**。Workflow App 的 API Key（`app-xxx`），与 curl 文档 `Bearer` 相同 |
| `DIFY_WORKFLOW_PRELIMINARY` | 填 `true` 启用初步诊断；**不要**把 `app-xxx` 写在这里 |
| `DIFY_PRELIM_OUTPUT_DIAGNOSIS_TEXT` | Dify 结束节点「诊断文本」变量名，默认 `text` |
| `DIFY_PRELIM_OUTPUT_BASIS` | 「依据」变量名（可选） |
| `DIFY_PRELIM_OUTPUT_CONFIDENCE` | 「置信度」变量名（可选） |
| `DIFY_PRELIM_OUTPUT_SUGGESTED_DISEASES` | 「建议疾病」变量名（可选，JSON 数组或字符串） |

`application.yml` 中设置 `xikang.ai.dify.enabled=true` 并填入上述变量。

### 联调步骤

1. 在 Dify 发布工作流，确认输入变量为 `text`、`preHandle`。
2. 记录「结束」节点输出变量名，写入 `preliminary-output-keys` 或环境变量。
3. 重启 `physician-service`，前端第 2 步 →「生成初步诊断」。
4. 失败时响应为通用错误文案；服务端日志含 `workflow_run_id`，不含患者全文。

### 其它 Dify（W1–W4）

- `DIFY_WORKFLOW_W1` … `DIFY_WORKFLOW_W4`：非空时尝试同一 API Key（与 preliminary 共用 Key 会命中同一 App，建议仅开启 preliminary）。
- `CT_INFERENCE_URL`：外部 CT 推理服务，未配置则用内置模拟。
