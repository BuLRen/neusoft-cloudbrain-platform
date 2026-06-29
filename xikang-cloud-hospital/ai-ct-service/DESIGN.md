# ai-ct-service 设计文档

> 状态：设计阶段，待评审。评审通过后再开始实现。
> 规矩：本项目"先文档后实现"，本文件是实现的唯一依据。代码不得偏离本文档。

---

## 0. 一句话定位

封装头部 CT 伪影检测模型（3D U-Net 多任务）的 **FastAPI 推理服务**。
输入一个 NIfTI 头部 CT，输出"有没有伪影、什么类型、多严重"。

- **是**：一个独立的、自包含的 HTTP 推理服务。
- **不是**：不碰业务逻辑、不接数据库、不注册 Nacos、不做鉴权、不做前端。
- **范围边界**：本服务只负责"能推理、有输入输出"。Java 侧（medtech-service）如何调用本服务，由队友完成，不在本文档范围内。

---

## 1. 这个服务是什么 / 不是什么

| 维度 | 结论 |
|------|------|
| 语言/框架 | Python 3.10+ / FastAPI / Uvicorn |
| 在仓库中的位置 | `xikang-cloud-hospital/ai-ct-service/`（与 `medtech-service` 等平级） |
| 是否进 Maven `pom.xml` 的 `<modules>` | **否**。Python 服务不归 Maven 管，否则 `mvn compile` 会报错。 |
| 是否注册 Nacos | **否**。只服务单一调用方（medtech），HTTP 直连即可，与 `AiPharmacyClient` 直连 8104 同模式。 |
| 端口 | **8105**（8100-8104 已占用） |
| 鉴权 | **无**。需要鉴权由调用方（网关/medtech）负责，本服务裸暴露在内网。 |
| 数据持久化 | **无**。无数据库，无文件落盘（上传文件在内存/临时目录处理完即丢）。 |
| 推理模式 | **同步**。CPU 推理，单次约十几秒到一两分钟，靠调用方的超时兜底。**不上任务队列**，简化实现。 |
| 并发 | 默认 **串行**（一次只跑一个推理），避免 CPU 被打爆。可配置。 |

---

## 2. 模型说明（封装的是什么）

来源：`CQ500-data-clean/train/` 下训练得到的 `MultiTaskUNet3D`。

### 2.1 输入契约（必须严格匹配训练时的预处理）
- 张量形状：`(1, 1, D, H, W)`，单通道。
- 预处理流水线（与 `transform.py` 的 `common_pre` 完全一致，**不可改动**，否则推理结果失真）：
  1. RAS 方向标准化（`Orientationd axcodes="RAS"`）
  2. 重采样到 spacing `(2.0, 2.0, 2.5) mm`（x, y, z）
  3. 脑窗 HU 归一化：`(-80, 80) → (0, 1)`，clip=True
- 训练 patch 尺寸：`(32, 224, 224)`（D, H, W）

### 2.2 输出契约（模型同时吐两路）
| 输出 | 形状 | 含义 |
|------|------|------|
| `seg` | `(1, 5, D, H, W)` | 逐体素 5 类分割 logits。5 类：`0 背景 / 1 金属 / 2 线束硬化 / 3 部分容积 / 4 环形` |
| `cls` | `(1, 5)` | 图像级多标签 logits。5 维：`[有无伪影, 金属, 线束硬化, 部分容积, 环形]` |

### 2.3 模型定义文件
- **直接拷贝** `CQ500-data-clean/train/model.py` 到本服务的 `app/model.py`，**一字不改**。
- 加载 `.pth` 必须用与训练时完全相同的结构定义，否则 `state_dict` 的 key 对不上会报错。

### 2.4 能力边界（必须如实认知）
- ✅ 能做：判断有无伪影、伪影类型分类、伪影区域分割。
- ❌ 不能做：病灶诊断、出血分级、肿瘤检测等。模型名字虽然叫"诊断"，实际是"伪影质控"。
- ⚠️ 训练集只有约 12 个 series，在新数据上的泛化能力**存疑**。本服务对外是"辅助质控工具"，**不得宣称为诊断工具，不得替代医师阅片**。

---

## 3. 推理流水线（服务内部怎么处理一次请求）

```
HTTP 上传 .nii / .nii.gz
        │
        ▼
① 解析：SimpleITK 读取 → numpy 体数据 + 原始 spacing/direction
        │
        ▼
② 预处理（与训练 common_pre 一致）
   - Orientation → RAS
   - Spacing → (2.0, 2.0, 2.5)
   - ScaleIntensity → 脑窗 (-80,80)→(0,1)
        │
        ▼
③ 滑窗推理（核心难点）
   - 整卷可能远大于 (32,224,224)，必须切块逐块推理再拼回
   - 切块大小：(32, 224, 224)
   - 步长：每个维度 50% 重叠
   - 重叠区：高斯权重加权平均（避免拼接缝隙）
   - 不足一个 patch 的边角：zero-pad 到 patch 尺寸再推
        │
        ▼
④ 后处理
   - seg logits → softmax → argmax → 每个体素一个类别 (0..4)
   - cls logits → sigmoid → 5 维概率
   - 计算"伪影体素占比" = (seg>0 的体素数) / 总体素数
   - 由占比映射严重程度（阈值见 §6）
        │
        ▼
⑤ 组装 JSON 返回
```

### 3.1 为什么要滑窗
训练时模型只见固定 `(32, 224, 224)` 的 patch。真实头部 CT 重采样后尺寸不固定（比如可能 `(40, 280, 280)`），直接整卷塞进模型：
- 要么尺寸不符报错，
- 要么显存/内存爆炸。

滑窗是医学 3D 推理的标准做法：把大 volume 切成 patch 网格，逐块推理，重叠区加权平均拼回去。

### 3.2 滑窗策略细节（实现时严格按此）
- **patch**: `(32, 224, 224)`
- **overlap**: 50%（步长 = patch // 2）
- **padding**: 边角不足部分用 0 补齐到 patch 整数倍，推理后裁回原尺寸
- **融合**: 重叠区域用 **3D 高斯权重**（中心高、边缘低）加权累加 logits，最后除以权重和。这样拼接处平滑，没有方块感。
- **分类头 `cls`**：每个 patch 都会产出一个 cls 向量。整卷的 cls = **所有 patch 的 cls logits 取平均**，再 sigmoid。

---

## 4. 接口契约

### 4.1 `POST /analyze` — 推理接口

**请求**：`multipart/form-data`
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | NIfTI 文件（`.nii` 或 `.nii.gz`） |

**响应**：`200 OK`，JSON
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

**字段说明**：
| 字段 | 类型 | 含义 |
|------|------|------|
| `has_artifact` | bool | `cls[0]` 经 sigmoid 后 > 0.5 则为 true |
| `artifact_types` | object | 4 种伪影各自的概率（`cls[1..4]` sigmoid 后的值），键固定为 `metal/beam_hardening/partial_volume/ring` |
| `artifact_volume_ratio` | float | 分割图中类别 1-4 的体素数 / 总体素数。范围 [0, 1] |
| `severity` | string | 由 `artifact_volume_ratio` 映射，见 §6 |
| `inference_ms` | int | 纯模型推理耗时（不含 IO），便于性能排查 |

**统一响应格式**：沿用医院 `{code, message, data}` 风格，让队友调用时解析逻辑和别的服务一致。

**错误响应**：
| 场景 | HTTP | code | message |
|------|------|------|---------|
| 文件格式不是 NIfTI | 400 | 4001 | "文件格式无效，仅支持 NIfTI (.nii/.nii.gz)" |
| 文件解析失败 | 422 | 4221 | "NIfTI 文件解析失败" |
| 推理过程异常 | 500 | 5001 | "推理失败：<详情>" |

### 4.2 `GET /health` — 健康检查

**响应**：
```json
{
  "status": "up",
  "model_loaded": true,
  "device": "cpu"
}
```
- `model_loaded`：启动时若权重加载失败，这里返 `false`，便于排查。
- 这个接口给部署/运维用，**不返医院统一 `{code,message,data}` 格式**（K8s/负载均衡器习惯裸 JSON）。

---

## 5. 目录结构

```
ai-ct-service/
├── DESIGN.md                  本文档
├── README.md                  启动方式 + 给队友的交接说明
├── requirements.txt           Python 依赖
├── .gitignore                 忽略 models/*.pth、__pycache__、临时文件
├── app/
│   ├── __init__.py
│   ├── main.py                FastAPI 入口、路由、启动时加载模型
│   ├── model.py               MultiTaskUNet3D（从 train/model.py 拷贝，不改）
│   ├── preprocess.py          NIfTI 读取 + 预处理（RAS/spacing/脑窗）
│   ├── inference.py           滑窗推理 + 后处理 + 结果组装
│   └── config.py              配置常量（端口、patch 尺寸、阈值、路径）
├── models/
│   └── best_model.pth         训练好的权重（用户提供，不入 git）
└── samples/                   （可选）测试用 NIfTI，方便自测
```

**文件职责红线**：
- `model.py` 必须和训练时完全一致，**任何"优化重构"都禁止**，否则权重加载失败。
- 预处理常量（HU 窗、spacing、patch 尺寸）集中在 `config.py`，与 `transform.py` 中的值一一对应。

---

## 6. 配置常量（集中在 config.py）

```python
# 服务
SERVICE_HOST = "0.0.0.0"
SERVICE_PORT = 8105

# 模型
MODEL_PATH = "models/best_model.pth"   # 相对服务根目录
DEVICE = "cpu"                          # 无 GPU
NUM_SEG_CLASSES = 5
NUM_CLS = 5
ARTIFACT_CLASS_NAMES = [
    "background", "metal", "beam_hardening", "partial_volume", "ring"
]

# 预处理（必须与 train/transform.py 完全一致，不可改）
HU_MIN, HU_MAX = -80.0, 80.0
SPACING = (2.0, 2.0, 2.5)       # (x, y, z) mm
PATCH_SIZE = (32, 224, 224)     # (D, H, W)
OVERLAP = 0.5                    # 滑窗重叠率

# 后处理阈值
CLS_THRESHOLD = 0.5              # cls[0] > 此值判定 has_artifact
# 严重程度映射（按伪影体素占比）
SEVERITY_THRESHOLDS = [
    (0.00, "clean"),     # ratio == 0
    (0.001, "mild"),     # 0 < ratio < 0.5%
    (0.02,  "moderate"), # 0.5% ≤ ratio < 2%
    (0.05,  "severe"),   # ratio ≥ 2% → 重扫建议
]

# 并发（CPU 保护）
MAX_CONCURRENT_INFERENCES = 1    # 同时只跑一个推理
```

> 阈值数字是初值，第一次跑真实数据后可调。改动只动 config.py，不动业务代码。

---

## 7. 依赖清单（requirements.txt）

```
fastapi
uvicorn[standard]
python-multipart        # 处理文件上传
torch                   # CPU 版即可，无需 CUDA
numpy
SimpleITK              # NIfTI/DICOM 读写
monai                  # 复用训练时的 transforms（Spacingd 等）
pydantic
```

说明：
- `torch` 装 **CPU 版**（`pip install torch --index-url https://download.pytorch.org/whl/cpu`），服务器无 GPU。
- `monai` 是为了和训练时的 `transform.py` 用同一套预处理实现，保证一致性。**不复用会导致预处理细微差异，推理结果失真**。

---

## 8. 启动方式

```bash
cd ai-ct-service

# 1. 装依赖（建议用 venv）
python -m venv .venv
.venv\Scripts\activate         # Windows
# source .venv/bin/activate    # Linux
pip install -r requirements.txt

# 2. 放权重
# 把 best_model.pth 放到 models/best_model.pth

# 3. 启动
uvicorn app.main:app --host 0.0.0.0 --port 8105

# 4. 自测
curl -X POST http://localhost:8105/analyze -F "file=@sample.nii.gz"
curl http://localhost:8105/health
```

启动时模型一次性加载到内存，常驻。`main.py` 用 FastAPI 的 `lifespan` 在启动钩子里 load，避免每个请求重复加载。

---

## 9. 给队友的交接说明（写进 README）

队友要接入时，告诉他三件事：

1. **服务地址**：`http://<host>:8105`
2. **怎么调**：`POST /analyze`，`multipart/form-data` 上传 NIfTI 文件，同步等返回（CPU 推理可能十几秒到一分钟，请把调用方超时设到 ≥ 120 秒）。
3. **返回什么**：见本文档 §4.1。`data.has_artifact` 是核心结论，`data.artifact_types` 是分类概率，`data.severity` 是严重程度。

**Java 侧参考**：和 `pharmacy-service` 里的 `AiPharmacyClient`（RestTemplate 直连）一个写法，把 `base-url` 换成 `http://localhost:8105` 即可。medtech-service 里已预留 `xikang.ai.dify.ct-inference-url` 配置项和 `CtInferenceService` 接入位（目前是 mock），队友把 mock 分支换成真实 HTTP 调用即可。

---

## 10. 风险与已知限制

1. **CPU 推理慢**：单次十几秒到一分钟。同步模式下，调用方必须设足够大的超时（建议 ≥ 120s）。若未来并发上来，需引入任务队列（Celery/Redis），但本期不做。
2. **模型泛化存疑**：训练数据量小（约 12 个 series）。第一次跑真实 CT 时结果可能不理想，这是数据问题，不是封装问题。
3. **文件大小**：NIfTI 通常几十 MB，multipart 上传在内网可接受；跨公网不建议。生产环境建议改为"共享存储 + 传路径"或对象存储，但本期按 multipart 简化实现。
4. **仅支持 NIfTI**：真实医院 CT 设备产出的是 DICOM。DICOM 支持留作后续扩展（SimpleITK 能读 DICOM series，接口层加一个分支即可），本期不做。
5. **无鉴权**：服务裸暴露，必须部署在内网/仅对 medtech 可见。

---

## 11. 不在本期范围（明确划掉）

- ❌ Java 侧（medtech）调用改造 —— 队友做
- ❌ Nacos 注册、Spring AI 集成 —— 不需要
- ❌ 前端可视化（分割掩码叠加显示）—— 二期
- ❌ DICOM 输入支持 —— 二期
- ❌ 任务队列 / 异步推理 —— 起步用同步，需要时再加
- ❌ 数据库 / 结果持久化 —— 不需要
- ❌ 鉴权 / 多租户 —— 由网关负责
