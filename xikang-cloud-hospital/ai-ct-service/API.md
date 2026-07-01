# ai-ct-service API 文档

> 版本：1.0.0  
> 基础地址：`http://<host>:8105`  
> 协议：HTTP/1.1  
> 数据格式：JSON（`/analyze` 请求为 `multipart/form-data`）

---

## 1. 服务概述

`ai-ct-service` 是一个基于 **FastAPI** 的头部 CT **伪影检测**推理服务。它封装了训练好的 3D U-Net 多任务模型（`MultiTaskUNet3D`），通过 HTTP 接收 NIfTI 格式的 CT 体数据，同步返回伪影检测结果。

### 1.1 能力边界

| 能做 | 不能做 |
|------|--------|
| 判断 CT 是否存在伪影 | 病灶诊断、出血分级、肿瘤检测 |
| 识别伪影类型（金属、线束硬化、部分容积、环形） | 替代医师阅片 |
| 评估伪影严重程度 | DICOM 直接输入（本期仅支持 NIfTI） |

### 1.2 架构特点

- **同步推理**：请求上传后阻塞等待，直到推理完成再返回
- **CPU 推理**：默认无 GPU，单次推理约 **15 秒 ~ 2 分钟**
- **串行处理**：同时只处理 **1** 个推理请求，并发请求返回 `503`
- **无鉴权**：需部署在内网，由调用方（如 medtech-service）负责访问控制
- **无持久化**：上传文件写入临时目录，推理完成后立即删除

### 1.3 处理流水线

```
上传 NIfTI (.nii / .nii.gz)
    → SimpleITK 读取
    → 预处理（RAS 方向 / 重采样 2.0×2.0×2.5mm / 脑窗 HU -80~80 归一化）
    → 滑窗推理（patch 32×224×224，50% 重叠，高斯融合）
    → 后处理（分类概率 + 分割体素占比 + 严重程度映射）
    → JSON 响应
```

---

## 2. 快速开始

### 2.1 安装依赖

```bash
cd xikang-cloud-hospital/ai-ct-service

python -m venv .venv
source .venv/bin/activate          # Linux/macOS
# .venv\Scripts\activate           # Windows

# 先装 CPU 版 PyTorch
pip install torch --index-url https://download.pytorch.org/whl/cpu

pip install -r requirements.txt
```

### 2.2 放置模型权重

将训练好的权重文件放到：

```
ai-ct-service/models/best_model.pth
```

权重不存在时服务仍可启动，但 `/analyze` 会返回 `503`（模型未加载）。

### 2.3 启动服务

```bash
# 方式一（推荐）
uvicorn app.main:app --host 0.0.0.0 --port 8105

# 方式二
python -m app.main
```

默认监听 `0.0.0.0:8105`，可在 `app/config.py` 中修改 `SERVICE_HOST` / `SERVICE_PORT`。

### 2.4 验证服务

```bash
curl http://localhost:8105/health
```

期望返回 `"status": "up"` 且 `"model_loaded": true`。

---

## 3. 通用约定

### 3.1 响应格式

`/analyze` 接口采用与医院其他服务一致的三段式结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 业务状态码，`200` 表示成功 |
| `message` | string | 人类可读描述 |
| `data` | object \| null | 成功时为结果对象；失败时为 `null` |

`/health` 接口**不**使用上述格式，直接返回裸 JSON（便于 K8s / 负载均衡探活）。

### 3.2 错误码一览

| HTTP 状态码 | 业务 code | 场景 |
|-------------|-----------|------|
| 200 | 200 | 推理成功 |
| 400 | 4001 | 上传文件不是 NIfTI 格式 |
| 422 | 4221 | NIfTI 文件解析失败 |
| 500 | 5001 | 推理过程内部异常 |
| 503 | 5002 | 模型权重未加载 |
| 503 | 5003 | 服务繁忙（已有推理在执行） |

错误响应示例：

```json
{
  "code": 4001,
  "message": "文件格式无效，仅支持 NIfTI (.nii/.nii.gz)",
  "data": null
}
```

### 3.3 调用方注意事项

1. **超时设置**：建议 HTTP 客户端超时 ≥ **120 秒**
2. **文件格式**：仅支持 `.nii` 和 `.nii.gz`，不支持 DICOM
3. **并发限制**：同一实例同时只处理 1 个请求，高并发场景需排队或水平扩展
4. **输入数据**：应为头部 CT 的 NIfTI 文件，HU 值在预处理阶段按脑窗 `[-80, 80]` 裁剪归一化

---

## 4. 接口详情

### 4.1 健康检查

检查服务是否存活，以及模型是否已成功加载。

```
GET /health
```

#### 请求

无请求体，无必填参数。

#### 成功响应 `200 OK`

```json
{
  "status": "up",
  "model_loaded": true,
  "device": "cpu"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 固定为 `"up"`，表示进程正常 |
| `model_loaded` | bool | `true` 表示权重已加载，可正常推理 |
| `device` | string | 推理设备，当前固定为 `"cpu"` |

#### 使用场景

- Kubernetes `livenessProbe` / `readinessProbe`
- 部署后确认 `models/best_model.pth` 是否加载成功
- 若 `model_loaded` 为 `false`，检查权重路径和启动日志

#### 示例

```bash
curl http://localhost:8105/health
```

---

### 4.2 CT 伪影分析（核心接口）

上传 NIfTI 头部 CT 文件，执行伪影检测推理。

```
POST /analyze
Content-Type: multipart/form-data
```

#### 请求参数

| 字段名 | 位置 | 类型 | 必填 | 说明 |
|--------|------|------|------|------|
| `file` | form-data | file | 是 | NIfTI 文件，扩展名须为 `.nii` 或 `.nii.gz` |

#### 成功响应 `200 OK`

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "has_artifact": true,
    "artifact_types": {
      "metal": 0.92,
      "beam_hardening": 0.31,
      "partial_volume": 0.05,
      "ring": 0.02
    },
    "artifact_volume_ratio": 0.087,
    "severity": "severe",
    "inference_ms": 48230
  }
}
```

#### 响应字段说明（`data`）

| 字段 | 类型 | 取值范围 | 说明 |
|------|------|----------|------|
| `has_artifact` | bool | — | 图像级分类：模型 `cls[0]` 经 sigmoid 后 > 0.5 则为 `true`，表示检测到伪影 |
| `artifact_types` | object | 各键 0.0 ~ 1.0 | 四种伪影类型的概率（`cls[1..4]` sigmoid 后） |
| `artifact_types.metal` | float | 0.0 ~ 1.0 | 金属伪影概率 |
| `artifact_types.beam_hardening` | float | 0.0 ~ 1.0 | 线束硬化伪影概率 |
| `artifact_types.partial_volume` | float | 0.0 ~ 1.0 | 部分容积效应概率 |
| `artifact_types.ring` | float | 0.0 ~ 1.0 | 环形伪影概率 |
| `artifact_volume_ratio` | float | 0.0 ~ 1.0 | 分割结果中伪影体素（类别 1~4）占总体素的比例 |
| `severity` | string | 见下表 | 由 `artifact_volume_ratio` 映射的严重程度 |
| `inference_ms` | int | ≥ 0 | 纯模型推理耗时（毫秒），不含文件 IO 和预处理 |

#### `severity` 严重程度映射

| `artifact_volume_ratio` | `severity` | 含义 |
|-------------------------|------------|------|
| `= 0` | `clean` | 无伪影体素 |
| `> 0` 且 `< 0.005` | `mild` | 轻微（< 0.5%） |
| `≥ 0.005` 且 `< 0.02` | `moderate` | 中等（0.5% ~ 2%） |
| `≥ 0.02` | `severe` | 严重（≥ 2%，建议重扫） |

> 阈值定义在 `app/config.py` 的 `SEVERITY_THRESHOLDS`，可按实际数据调优。

#### 错误响应

**400 — 文件格式无效**

```json
{
  "code": 4001,
  "message": "文件格式无效，仅支持 NIfTI (.nii/.nii.gz)",
  "data": null
}
```

**422 — NIfTI 解析失败**

```json
{
  "code": 4221,
  "message": "NIfTI 文件解析失败: RuntimeError: ...",
  "data": null
}
```

**500 — 推理异常**

```json
{
  "code": 5001,
  "message": "推理失败: RuntimeError: ...",
  "data": null
}
```

**503 — 模型未加载**

```json
{
  "code": 5002,
  "message": "模型权重未加载，无法推理（权重文件不存在: ...）",
  "data": null
}
```

**503 — 服务繁忙**

```json
{
  "code": 5003,
  "message": "服务繁忙，已有推理任务在执行，请稍后重试",
  "data": null
}
```

#### 调用示例

**cURL**

```bash
curl -X POST http://localhost:8105/analyze \
  -F "file=@/path/to/head_ct.nii.gz"
```

**Python (requests)**

```python
import requests

url = "http://localhost:8105/analyze"
with open("head_ct.nii.gz", "rb") as f:
    resp = requests.post(
        url,
        files={"file": ("head_ct.nii.gz", f, "application/octet-stream")},
        timeout=120,
    )
resp.raise_for_status()
result = resp.json()
print(result["data"]["has_artifact"])
print(result["data"]["severity"])
```

**Java (RestTemplate)**

```java
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource("/path/to/head_ct.nii.gz"));

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.MULTIPART_FORM_DATA);

HttpEntity<MultiValueMap<String, Object>> request =
    new HttpEntity<>(body, headers);

RestTemplate restTemplate = new RestTemplate();
// 务必设置足够长的超时（≥ 120s）
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(10_000);
factory.setReadTimeout(120_000);
restTemplate.setRequestFactory(factory);

ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://localhost:8105/analyze", request, Map.class);
```

**JavaScript (fetch)**

```javascript
const formData = new FormData();
formData.append("file", fileInput.files[0]);

const response = await fetch("http://localhost:8105/analyze", {
  method: "POST",
  body: formData,
  signal: AbortSignal.timeout(120_000),
});
const result = await response.json();
```

---

## 5. OpenAPI / Swagger

服务启动后，FastAPI 自动生成交互式文档：

| 文档 | 地址 |
|------|------|
| Swagger UI | `http://localhost:8105/docs` |
| ReDoc | `http://localhost:8105/redoc` |
| OpenAPI JSON | `http://localhost:8105/openapi.json` |

可在 Swagger UI 中直接上传 NIfTI 文件进行测试。

---

## 6. 配置参考

关键配置位于 `app/config.py`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `SERVICE_HOST` | `0.0.0.0` | 监听地址 |
| `SERVICE_PORT` | `8105` | 监听端口 |
| `MODEL_PATH` | `models/best_model.pth` | 模型权重路径（相对服务根目录） |
| `DEVICE` | `cpu` | 推理设备 |
| `CLS_THRESHOLD` | `0.5` | `has_artifact` 判定阈值 |
| `MAX_CONCURRENT_INFERENCES` | `1` | 最大并发推理数 |

---

## 7. 与 Java 侧集成

本服务设计为被 `medtech-service` 通过 HTTP 直连调用（类似 `pharmacy-service` 的 `AiPharmacyClient` 模式）：

1. 配置服务地址，例如 `http://ai-ct-service:8105`
2. 使用 `RestTemplate` / `WebClient` 向 `POST /analyze` 发送 multipart 请求
3. 解析 `{code, message, data}` 响应，核心字段为 `data.has_artifact` 和 `data.severity`
4. medtech-service 中已预留 `xikang.ai.dify.ct-inference-url` 配置项

---

## 8. 已知限制

1. **推理速度慢**：CPU 串行推理，不适合高吞吐场景
2. **泛化能力有限**：训练数据约 12 个 series，新数据效果需验证
3. **仅 NIfTI**：DICOM 需先转换为 NIfTI
4. **无鉴权**：必须内网部署
5. **不返回分割掩码**：本期仅返回统计结果，掩码可视化属二期范围

---

## 9. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | — | 初始版本：`GET /health`、`POST /analyze` |
