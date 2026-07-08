# lung-nodule-seg-service

基于 Medical Segmentation Decathlon Task06_Lung 数据集训练的肺结节 / 肺癌 3D 分割推理服务。

## 架构

```
前端 Vue → ct-viewer-service (Java :8099) → lung-nodule-seg-service (Python :8222)
```

## 推理后端 / 多模型运行时选择

服务支持三种模型，**启动时会尝试加载全部已配置权重的模型**，前端 / 调用方在
每次请求时通过 `model_id` 字段任选一个，无需重启服务切换：

| model_id | 说明 | 网络规模 | 验证 Dice（Task06_Lung） | 默认是否启用 |
|------|------|----------|--------------------------|--------------|
| `monai`（默认） | 本仓库 `training/` 自训练的轻量 3D UNet，整卷滑窗推理 | 4 级，16~128 通道 | 取决于自训练轮数 | 是 |
| `segnet` | 移植自 [Ola-Vish/lung-tumor-segmentation](https://github.com/Ola-Vish/lung-tumor-segmentation)（MIT License）的 2D SegNet，VGG16-BN 编码器，逐轴位切片推理 | 2D，VGG16 规模 | 作者报告 0.88 / IoU 0.75 | 是（需手动放置权重，见下） |
| `nnunet` | 官方 [nnU-Net v2](https://github.com/MIC-DKFZ/nnUNet) 框架，加载 [Lab-Rasool/CLN-Segmenter-MSD-fold0](https://huggingface.co/Lab-Rasool/CLN-Segmenter-MSD-fold0) 预训练权重 | 6 级，32~320 通道 | 官方报告 0.7161 | 否（体积大，需显式开启） |

三者共用同一套连通域后处理（`app/postprocess.py`），返回的病灶字段完全一致，
Java/前端无需感知后端差异，只需在请求体里传不同的 `model_id`。

`LUNG_NODULE_SEG_BACKEND` / `LUNG_NODULE_SEG_DEFAULT_MODEL` 环境变量决定
**默认模型**（调用方不传 `model_id` 时使用哪一个），兼容旧版单后端部署方式。
`nnunet` 因依赖体积大，默认不加载，需要设置 `LUNG_NODULE_SEG_ENABLE_NNUNET=1`
（或 `LUNG_NODULE_SEG_BACKEND=nnunet`）才会尝试加载。

**重要提示**：无论哪个模型，训练数据都只是公开的 MSD Task06_Lung 63 例数据集，**未经过任何临床验证**，仅供研究/工程验证使用，不能作为临床诊断依据（详见下方"注意事项"）。

## 目录结构

```
lung-nodule-seg-service/
├── app/             推理服务（FastAPI）
│   ├── config.py    服务配置（端口/模型路径/预处理参数/多模型注册表）
│   ├── model.py     网络定义（MONAI UNet，与训练端一致，monai 后端使用）
│   ├── preprocess.py  NRRD 读取 + 重采样 + HU 归一化（monai 后端使用）
│   ├── inference.py   滑窗推理（monai 后端使用）
│   ├── segnet_model.py  SegNet 网络定义（移植自 Ola-Vish/lung-tumor-segmentation）
│   ├── segnet_checkpoint_utils.py  SegNet checkpoint 容错加载（兼容 Lightning ckpt）
│   ├── segnet_backend.py  SegNet 2D 逐切片推理封装（segnet 后端使用）
│   ├── nnunet_backend.py  nnU-Net v2 推理封装（nnunet 后端使用）
│   ├── postprocess.py 连通域分析 + 病灶指标计算（三种后端共用）
│   └── main.py      FastAPI 入口（/health, /internal/segment，多模型运行时分发）
├── training/        训练脚本（monai 后端的自训练模型）
│   ├── config.py    训练超参数
│   ├── dataset.py   加载 Task06_Lung 数据集
│   ├── transforms.py  MONAI Compose 预处理 + 数据增强
│   ├── model.py     与推理端完全一致的网络定义
│   ├── train.py     训练主脚本
│   ├── evaluate.py  验证集评估 + 可视化
│   └── requirements-train.txt
├── scripts/
│   ├── download_nnunet_weights.py     下载并整理 nnU-Net 预训练权重
│   └── convert_segnet_checkpoint.py   转换 SegNet checkpoint 为干净 state_dict
├── models/          模型权重（不入 git，手动放置 / 下载生成）
│   ├── best_model.pth          monai 后端权重
│   ├── segnet_lung_tumor.pth   segnet 后端权重（转换脚本产出）
│   └── nnunet_results/         nnunet 后端权重目录
├── requirements.txt         推理服务依赖（monai 后端）
├── requirements-nnunet.txt  nnunet 后端额外依赖
├── Dockerfile
└── README.md
```

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 训练模型

```bash
# 设置数据集路径
export LUNG_DATASET_ROOT=/path/to/Task06_Lung

# 快速 smoke test（验证流程）
python -m training.train --smoke-test

# 完整训练（Mac M2 约需数小时；GPU 机器约需 1-2h）
python -m training.train

# 从 checkpoint 续训
python -m training.train --resume models/last_model.pth
```

训练完成后 `models/best_model.pth` 自动保存最优验证 Dice 的权重。

### 3. 评估

```bash
python -m training.evaluate --save-vis
```

### 4. 启动推理服务（monai 后端，默认）

```bash
# 确保 models/best_model.pth 已就位
uvicorn app.main:app --host 0.0.0.0 --port 8222

# 或
python -m app.main
```

健康检查：

```bash
curl http://localhost:8222/health
# 有权重：{"ok":true,"model_loaded":true,"backend":"monai","device":"cpu","model_version":"LungNoduleSeg-v1.0","load_error":null}
# 无权重：{"ok":false,"model_loaded":false,...}
```

### 4'. 接入 SegNet 后端（轻量演示模型，可选但推荐低配设备使用）

[Ola-Vish/lung-tumor-segmentation](https://github.com/Ola-Vish/lung-tumor-segmentation)（MIT License）
提供了一个在 MSD Task06_Lung 上训练好的 2D SegNet（VGG16-BN 编码器），网络体积
远小于 nnU-Net，CPU 推理也很快，适合设备资源有限时的演示场景。**权重托管在
Google Drive，没有官方下载 API，需要手动下载 + 一次性转换**：

```bash
# 1. 打开原仓库 README「My SegNet checkpoint can be downloaded from this link」，
#    浏览器手动下载 checkpoint（.ckpt 文件，几十 ~ 上百 MB）

# 2. 转换成本服务可直接加载的干净权重（自动做 PyTorch-Lightning 兼容解析）
python -m scripts.convert_segnet_checkpoint --src ~/Downloads/你下载的文件.ckpt
# 默认写到 models/segnet_lung_tumor.pth，可用 --dst 自定义路径

# 3. 启动服务（不需要额外环境变量，segnet 与 monai 一样默认尝试加载）
python -m app.main
```

启动成功后，`GET /health` 的 `available_models` 里应能看到：

```json
{"id": "segnet", "label": "SegNet 2D（VGG16 编码器，轻量演示模型）", "loaded": true, ...}
```

若要让 SegNet 成为**默认模型**（未显式传 `model_id` 时使用），设置：

```bash
export LUNG_NODULE_SEG_DEFAULT_MODEL=segnet
```

**重要提示**：该权重来自第三方开源项目，仅在公开的 MSD Task06_Lung 63 例数据
集上训练，README 原作者也说明这仍是 work in progress，**未经任何临床验证**，
仅供研究/工程演示使用。

### 4''. 启动推理服务（nnunet 后端，默认不启用）

如果想对比自训练模型与官方 nnU-Net 权重的效果，可以额外启用 `nnunet` 后端
（体积较大，默认不加载）：

```bash
# 1. 安装额外依赖（体积较大）
pip install -r requirements.txt -r requirements-nnunet.txt

# 2. 下载并整理官方预训练权重（Task06_Lung, fold 0, Dice 0.7161）
python -m scripts.download_nnunet_weights

# 3. 启用 nnunet 后端并启动（不会影响 monai/segnet 的正常加载）
export LUNG_NODULE_SEG_ENABLE_NNUNET=1
uvicorn app.main:app --host 0.0.0.0 --port 8222
```

nnU-Net 网络更大，CPU 推理明显慢于 monai / segnet 后端，建议评估耗时是否满足
业务需求，必要时配 GPU。若要把它设为默认模型，额外设置
`LUNG_NODULE_SEG_DEFAULT_MODEL=nnunet`（或 `LUNG_NODULE_SEG_BACKEND=nnunet`，
会自动同时启用并设为默认）。

### 5. 测试分割接口

```bash
curl -X POST http://localhost:8222/internal/segment \
  -H "Content-Type: application/json" \
  -d '{
    "src_nrrd_path": "/path/to/volume.nrrd",
    "out_nrrd_path": "/path/to/mask.nrrd",
    "source_name": "测试样本",
    "model_id": "segnet"
  }'
```

`model_id` 可省略（此时使用 `LUNG_NODULE_SEG_DEFAULT_MODEL` 指定的默认模型），
可选值为 `monai` / `segnet` / `nnunet`（需已加载成功，见 `/health`）。

## API 说明

### GET /health

```json
{
  "ok": true,
  "model_loaded": true,
  "backend": "monai",
  "device": "cpu",
  "model_version": "LungNoduleSeg-v1.0",
  "load_error": null,
  "default_model_id": "monai",
  "available_models": [
    { "id": "monai", "label": "MONAI 3D UNet（自训练，默认）", "version": "LungNoduleSeg-v1.0", "backend": "monai", "device": "cpu", "loaded": true, "error": null },
    { "id": "segnet", "label": "SegNet 2D（VGG16 编码器，轻量演示模型）", "version": "LungTumorSeg-SegNet-OlaVish-v1", "backend": "segnet", "device": "cpu", "loaded": false, "error": "SegNet 权重文件不存在: ..." },
    { "id": "nnunet", "label": "nnU-Net v2（体积较大，默认不启用）", "version": "LungNoduleSeg-nnUNet-Task06Lung-fold0", "backend": "nnunet", "device": "cpu", "loaded": false, "error": "该模型未启用，请检查相关环境变量配置" }
  ],
  "inference_running": false,
  "inference_phase": null,
  "inference_elapsed_seconds": 0,
  "inference_source": null,
  "inference_model_id": null
}
```

`ok` / `model_loaded` / `backend` / `device` / `model_version` / `load_error`
是兼容旧版单模型部署的字段，映射到 `default_model_id` 对应模型的状态；
新增的 `available_models` 供前端渲染模型选择下拉框。

### POST /internal/segment

请求体（JSON）：

| 字段 | 类型 | 说明 |
|------|------|------|
| src_nrrd_path | string | 源 NRRD 文件绝对路径 |
| out_nrrd_path | string | 输出掩码 NRRD 绝对路径（服务自动创建目录）|
| source_name | string | 可选，体数据来源名称 |
| model_id | string | 可选，指定使用的模型：`monai` / `segnet` / `nnunet`；不传时使用 `default_model_id` |

响应（成功 200）：

```json
{
  "code": 200,
  "message": "AI 肺结节分割完成：检出 N 处疑似病灶，处理耗时 XXXX ms",
  "data": {
    "is_mask": true,
    "meta": { "shape_zyx": [D,H,W], "spacing_xyz": [x,y,z], ... },
    "lesions": [
      {
        "id": 1,
        "label": "病灶 1",
        "sliceIndex": 121,
        "plane": "axial",
        "centroidXyz": [x, y, z],
        "diameterMm": 8.6,
        "bbox": [d0, h0, w0, d1, h1, w1],
        "volumeMm3": 230.5,
        "volumeCm3": 0.23,
        "meanDensityHU": -623.0,
        "confidence": 0.87,
        "riskLevel": "中风险",
        "source": "deep_learning"
      }
    ],
    "summary": {
      "lesionCount": 1,
      "maxDiameterMm": 8.6,
      "totalVolumeMm3": 230.5,
      "totalVolumeCm3": 0.23,
      "overallRiskLevel": "中风险",
      "modelVersion": "LungNoduleSeg-v1.0",
      "processingTimeMs": 18000,
      "note": "AI 辅助结果仅供参考，非临床诊断依据，请结合临床综合判断。"
    },
    "message": "AI 肺结节分割完成：检出 1 处疑似病灶..."
  }
}
```

## 配置

通过环境变量覆盖默认值：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| LUNG_NODULE_SEG_PORT | 8222 | 服务端口 |
| LUNG_NODULE_SEG_BACKEND | monai | 默认模型（兼容旧版单后端部署）：`monai` \| `segnet` \| `nnunet` |
| LUNG_NODULE_SEG_DEFAULT_MODEL | 取 LUNG_NODULE_SEG_BACKEND | 显式指定默认模型（未传 model_id 时使用），优先级高于 LUNG_NODULE_SEG_BACKEND |
| LUNG_NODULE_SEG_ENABLE_NNUNET | 0（设为 nnunet 后端时自动为 1） | 是否在启动时尝试加载 nnunet 模型 |
| LUNG_NODULE_SEG_DEVICE | cpu（无CUDA时）| monai 后端推理设备 |
| LUNG_DATASET_ROOT | ~/Downloads/Task06_Lung | 训练数据根目录 |
| SEGNET_MODEL_PATH | models/segnet_lung_tumor.pth | SegNet 权重路径（由 convert_segnet_checkpoint.py 产出）|
| SEGNET_BATCH_SIZE | 4 | SegNet 逐切片推理的批大小 |
| LUNG_NODULE_SEG_SEGNET_DEVICE | 同 LUNG_NODULE_SEG_DEVICE | segnet 后端推理设备 |
| NNUNET_RESULTS_DIR | models/nnunet_results | nnU-Net 权重根目录 |
| NNUNET_DATASET_NAME | Dataset502_MSDLung | nnU-Net 数据集目录名 |
| NNUNET_TRAINER | nnUNetTrainer | nnU-Net trainer 名称 |
| NNUNET_PLANS | nnUNetPlans | nnU-Net plans 名称 |
| NNUNET_CONFIGURATION | 3d_fullres | nnU-Net 配置 |
| NNUNET_FOLD | 0 | 使用的 fold |
| NNUNET_CHECKPOINT | checkpoint_best.pth | checkpoint 文件名 |
| LUNG_NODULE_SEG_NNUNET_DEVICE | cpu（无CUDA时）| nnunet 后端推理设备 |
| NNUNET_USE_MIRRORING | 0 | 是否启用镜像 TTA（精度更高但慢 ~8 倍，CPU/MPS 建议关闭）|
| NNUNET_STEP_SIZE | 0.5 | 滑窗步长比例（tile_step_size）|
| NNUNET_RETURN_PROBABILITIES | 0 | 是否返回整幅 softmax 概率图；默认关闭以降低 nnU-Net 峰值内存，关闭后 confidence 用近似值 |
| LUNG_NODULE_SEG_FORCE_GC | 1 | 推理结束后主动触发 Python GC，帮助释放大数组引用 |

## 注意事项

- `models/best_model.pth`、`models/segnet_lung_tumor.pth`、`models/nnunet_results/` 均不入 git，需手动放置 / 脚本下载转换
- monai 后端推理在 Mac M2 CPU 上约 20-60 秒（取决于体数据大小），GPU 约 3-10 秒
- segnet 后端是 2D 逐切片推理，网络远小于 monai/nnunet，CPU 推理通常明显更快，适合低配设备演示，但精度/泛化性弱于 3D 模型，且原作者标注为 work in progress
- nnunet 后端网络更大（6 级、最高 320 通道），CPU 推理会明显更慢；默认启用低内存模式，不返回概率图，生产环境建议配 GPU；且默认不启用（LUNG_NODULE_SEG_ENABLE_NNUNET=0）
- 风险分级为简化尺寸阈值规则（非严格 Lung-RADS），UI 中已注明"仅供参考"
- 三种后端使用的训练数据都只是公开的 MSD Task06_Lung 63 例数据集（或其子集），**均未经过临床验证**，仅供研究/工程验证使用，不能作为临床诊断或治疗决策依据
- segnet 权重来自第三方开源项目 [Ola-Vish/lung-tumor-segmentation](https://github.com/Ola-Vish/lung-tumor-segmentation)（MIT License），代码架构已按其许可证移植进本仓库，权重文件本身需按 README 指引手动下载转换，不随本仓库分发
