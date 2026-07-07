# lung-nodule-seg-service

基于 Medical Segmentation Decathlon Task06_Lung 数据集训练的肺结节 / 肺癌 3D 分割推理服务。

## 架构

```
前端 Vue → ct-viewer-service (Java :8099) → lung-nodule-seg-service (Python :8222)
```

## 推理后端

服务支持两种可切换的推理后端，由环境变量 `LUNG_NODULE_SEG_BACKEND` 控制：

| 后端 | 说明 | 网络规模 | 验证 Dice（Task06_Lung） |
|------|------|----------|--------------------------|
| `monai`（默认） | 本仓库 `training/` 自训练的轻量 3D UNet | 4 级，16~128 通道 | 取决于自训练轮数 |
| `nnunet` | 官方 [nnU-Net v2](https://github.com/MIC-DKFZ/nnUNet) 框架，加载 [Lab-Rasool/CLN-Segmenter-MSD-fold0](https://huggingface.co/Lab-Rasool/CLN-Segmenter-MSD-fold0) 预训练权重 | 6 级，32~320 通道 | 官方报告 0.7161 |

两者共用同一套连通域后处理（`app/postprocess.py`），返回的病灶字段完全一致，Java/前端无需感知后端差异。

**重要提示**：无论哪种后端，训练数据都只是公开的 MSD Task06_Lung 63 例数据集，**未经过任何临床验证**，仅供研究/工程验证使用，不能作为临床诊断依据（详见下方"注意事项"）。

## 目录结构

```
lung-nodule-seg-service/
├── app/             推理服务（FastAPI）
│   ├── config.py    服务配置（端口/模型路径/预处理参数/后端选择）
│   ├── model.py     网络定义（MONAI UNet，与训练端一致，monai 后端使用）
│   ├── preprocess.py  NRRD 读取 + 重采样 + HU 归一化（monai 后端使用）
│   ├── inference.py   滑窗推理（monai 后端使用）
│   ├── nnunet_backend.py  nnU-Net v2 推理封装（nnunet 后端使用）
│   ├── postprocess.py 连通域分析 + 病灶指标计算（两种后端共用）
│   └── main.py      FastAPI 入口（/health, /internal/segment）
├── training/        训练脚本（monai 后端的自训练模型）
│   ├── config.py    训练超参数
│   ├── dataset.py   加载 Task06_Lung 数据集
│   ├── transforms.py  MONAI Compose 预处理 + 数据增强
│   ├── model.py     与推理端完全一致的网络定义
│   ├── train.py     训练主脚本
│   ├── evaluate.py  验证集评估 + 可视化
│   └── requirements-train.txt
├── scripts/
│   └── download_nnunet_weights.py  下载并整理 nnU-Net 预训练权重
├── models/          模型权重（不入 git，手动放置 / 下载生成）
│   ├── best_model.pth       monai 后端权重
│   └── nnunet_results/      nnunet 后端权重目录
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

### 4'. 启动推理服务（nnunet 后端，可选）

如果不想自己训练，或想对比自训练模型与官方 nnU-Net 权重的效果，可以切换到 `nnunet` 后端：

```bash
# 1. 安装额外依赖（体积较大）
pip install -r requirements.txt -r requirements-nnunet.txt

# 2. 下载并整理官方预训练权重（Task06_Lung, fold 0, Dice 0.7161）
python -m scripts.download_nnunet_weights

# 3. 切换后端并启动
export LUNG_NODULE_SEG_BACKEND=nnunet
uvicorn app.main:app --host 0.0.0.0 --port 8222
```

健康检查会显示 `"backend":"nnunet"`。nnU-Net 网络更大，CPU 推理明显慢于 monai 后端，建议评估耗时是否满足业务需求，必要时配 GPU。

### 5. 测试分割接口

```bash
curl -X POST http://localhost:8222/internal/segment \
  -H "Content-Type: application/json" \
  -d '{
    "src_nrrd_path": "/path/to/volume.nrrd",
    "out_nrrd_path": "/path/to/mask.nrrd",
    "source_name": "测试样本"
  }'
```

## API 说明

### GET /health

```json
{
  "ok": true,
  "model_loaded": true,
  "backend": "monai",
  "device": "cpu",
  "model_version": "LungNoduleSeg-v1.0",
  "load_error": null
}
```

### POST /internal/segment

请求体（JSON）：

| 字段 | 类型 | 说明 |
|------|------|------|
| src_nrrd_path | string | 源 NRRD 文件绝对路径 |
| out_nrrd_path | string | 输出掩码 NRRD 绝对路径（服务自动创建目录）|
| source_name | string | 可选，体数据来源名称 |

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
| LUNG_NODULE_SEG_BACKEND | monai | 推理后端：`monai` \| `nnunet` |
| LUNG_NODULE_SEG_DEVICE | cpu（无CUDA时）| monai 后端推理设备 |
| LUNG_DATASET_ROOT | ~/Downloads/Task06_Lung | 训练数据根目录 |
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

## 注意事项

- `models/best_model.pth`、`models/nnunet_results/` 均不入 git，需手动放置 / 脚本下载
- monai 后端推理在 Mac M2 CPU 上约 20-60 秒（取决于体数据大小），GPU 约 3-10 秒
- nnunet 后端网络更大（6 级、最高 320 通道），CPU 推理会明显更慢，建议先用少量样本评估耗时，生产环境建议配 GPU
- 风险分级为简化尺寸阈值规则（非严格 Lung-RADS），UI 中已注明"仅供参考"
- 两种后端使用的训练数据都只是公开的 MSD Task06_Lung 63 例数据集，**均未经过临床验证**，仅供研究/工程验证使用，不能作为临床诊断或治疗决策依据
