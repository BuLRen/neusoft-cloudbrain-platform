# ai-ct-service

> 封装头部 CT 伪影检测模型（3D U-Net 多任务）的 FastAPI 推理服务。
> 输入一个 NIfTI 头部 CT，输出"有没有伪影、什么类型、多严重"。
>
> 详细设计见 [DESIGN.md](./DESIGN.md)。

---

## 1. 这是什么

- **是**：一个独立的、自包含的 HTTP 推理服务。
- **不是**：不碰业务逻辑、不接数据库、不注册 Nacos、不做鉴权、不做前端。
- Java 侧（medtech-service）如何调用本服务，由队友完成，不在本服务范围内。

### 能力边界（务必如实认知）

- ✅ 能做：判断有无伪影、伪影类型分类、伪影区域分割。
- ❌ 不能做：病灶诊断、出血分级、肿瘤检测等。模型虽叫"诊断"，实际是"伪影质控"。
- ⚠️ 训练集只有约 12 个 series，在新数据上的泛化能力**存疑**。本服务是"辅助质控工具"，**不得宣称为诊断工具，不得替代医师阅片**。

---

## 2. 快速启动

### 2.1 装依赖（建议用 venv）

```bash
cd ai-ct-service

# 建议用虚拟环境
python -m venv .venv
.venv\Scripts\activate         # Windows
# source .venv/bin/activate    # Linux

# torch 先装 CPU 版（服务器无 GPU）
pip install torch --index-url https://download.pytorch.org/whl/cpu

# 其余依赖
pip install -r requirements.txt
```

> 如果 `monai` 体积太大装不上，可降级方案：把 `app/preprocess.py` 改成用 SimpleITK 原生 API 手写 RAS/spacing/脑窗。但**强烈建议装 monai**，保证和训练预处理完全一致，否则推理结果会失真。

### 2.2 放权重

把训练好的权重放到：

```
ai-ct-service/models/best_model.pth
```

权重不存在也能启动服务，`/health` 会返回 `model_loaded: false`，`/analyze` 会返回明确错误"模型权重未加载"。权重放进去重启即可正常推理。

### 2.3 启动

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8105
```

### 2.4 自测

```bash
# 健康检查
curl http://localhost:8105/health
# {"status":"up","model_loaded":true,"device":"cpu"}

# 推理
curl -X POST http://localhost:8105/analyze -F "file=@sample.nii.gz"
```

---

## 3. 给队友的交接说明

队友要接入时，告诉他三件事：

1. **服务地址**：`http://<host>:8105`
2. **怎么调**：`POST /analyze`，`multipart/form-data` 上传 NIfTI 文件，同步等返回。
   - CPU 推理可能十几秒到一分钟，**调用方超时务必设到 ≥ 120 秒**。
3. **返回什么**：见下方 §4。

### Java 侧参考

和 `pharmacy-service` 里的 `AiPharmacyClient`（RestTemplate 直连）一个写法，把 `base-url` 换成 `http://localhost:8105` 即可。
medtech-service 里已预留 `xikang.ai.dify.ct-inference-url` 配置项和 `CtInferenceService` 接入位（目前是 mock），把 mock 分支换成真实 HTTP 调用即可。

---

## 4. 接口契约

### 4.1 `POST /analyze` —— 推理接口

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | NIfTI 文件（`.nii` 或 `.nii.gz`） |

**成功响应**：`200 OK`

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

| 字段 | 类型 | 含义 |
|------|------|------|
| `has_artifact` | bool | `cls[0]` 经 sigmoid 后 > 0.5 则为 true |
| `artifact_types` | object | 4 种伪影各自的概率（`cls[1..4]` sigmoid 后），键固定 `metal/beam_hardening/partial_volume/ring` |
| `artifact_volume_ratio` | float | 分割图中类别 1-4 的体素数 / 总体素数，范围 [0, 1] |
| `severity` | string | 由 `artifact_volume_ratio` 映射：`clean / mild / moderate / severe` |
| `inference_ms` | int | 纯模型推理耗时（不含 IO），便于性能排查 |

**错误响应**：

| 场景 | HTTP | code | message |
|------|------|------|---------|
| 文件格式不是 NIfTI | 400 | 4001 | "文件格式无效，仅支持 NIfTI (.nii/.nii.gz)" |
| 文件解析失败 | 422 | 4221 | "NIfTI 文件解析失败: ..." |
| 推理过程异常 | 500 | 5001 | "推理失败: ..." |
| 模型权重未加载 | 503 | 5002 | "模型权重未加载，无法推理" |
| 服务繁忙（已有推理在跑） | 503 | 5003 | "服务繁忙，已有推理任务在执行..." |

### 4.2 `GET /health` —— 健康检查

```json
{
  "status": "up",
  "model_loaded": true,
  "device": "cpu"
}
```

裸 JSON，**不走**统一 `{code,message,data}` 格式（供 K8s/负载均衡器用）。

---

## 5. 目录结构

```
ai-ct-service/
├── DESIGN.md                  设计文档（实现的唯一依据）
├── README.md                  本文档
├── requirements.txt           Python 依赖
├── .gitignore
├── app/
│   ├── __init__.py
│   ├── main.py                FastAPI 入口、路由、lifespan 加载模型
│   ├── model.py               MultiTaskUNet3D（从 train/model.py 拷贝，不改）
│   ├── preprocess.py          NIfTI 读取 + 预处理（RAS/spacing/脑窗）
│   ├── inference.py           滑窗推理 + 后处理 + 结果组装
│   └── config.py              配置常量
├── models/
│   └── best_model.pth         权重（用户提供，不入 git）
└── samples/                   （可选）测试用 NIfTI
```

### 文件职责红线

- `app/model.py` 必须和训练时完全一致，**任何"优化重构"都禁止**，否则权重加载失败。
- 预处理常量（HU 窗、spacing、patch 尺寸）集中在 `app/config.py`，与 `train/transform.py` 一一对应。

---

## 6. 已知限制（详见 DESIGN §10）

1. **CPU 推理慢**：单次十几秒到一分钟。调用方超时务必 ≥ 120s。
2. **模型泛化存疑**：训练数据量小（约 12 个 series）。第一次跑真实 CT 结果可能不理想，这是数据问题，不是封装问题。
3. **仅支持 NIfTI**：DICOM 支持留作后续扩展。
4. **无鉴权**：服务裸暴露，必须部署在内网/仅对 medtech 可见。
5. **并发=1**：同时只跑一个推理，避免 CPU 被打爆。需更高并发时引入任务队列（Celery/Redis）。
