# lung-nodule-seg-service

基于 Medical Segmentation Decathlon Task06_Lung 数据集训练的肺结节 / 肺癌 3D 分割推理服务。

## 架构

```
前端 Vue → ct-viewer-service (Java :8099) → lung-nodule-seg-service (Python :8107)
```

## 目录结构

```
lung-nodule-seg-service/
├── app/             推理服务（FastAPI）
│   ├── config.py    服务配置（端口/模型路径/预处理参数）
│   ├── model.py     网络定义（MONAI UNet，与训练端一致）
│   ├── preprocess.py  NRRD 读取 + 重采样 + HU 归一化
│   ├── inference.py   滑窗推理
│   ├── postprocess.py 连通域分析 + 病灶指标计算
│   └── main.py      FastAPI 入口（/health, /internal/segment）
├── training/        训练脚本
│   ├── config.py    训练超参数
│   ├── dataset.py   加载 Task06_Lung 数据集
│   ├── transforms.py  MONAI Compose 预处理 + 数据增强
│   ├── model.py     与推理端完全一致的网络定义
│   ├── train.py     训练主脚本
│   ├── evaluate.py  验证集评估 + 可视化
│   └── requirements-train.txt
├── models/          模型权重（不入 git，手动放置）
│   └── best_model.pth
├── requirements.txt 推理服务依赖
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

### 4. 启动推理服务

```bash
# 确保 models/best_model.pth 已就位
uvicorn app.main:app --host 0.0.0.0 --port 8107

# 或
python -m app.main
```

健康检查：

```bash
curl http://localhost:8107/health
# 有权重：{"ok":true,"model_loaded":true,"device":"cpu","model_version":"LungNoduleSeg-v1.0"}
# 无权重：{"ok":false,"model_loaded":false,...}
```

### 5. 测试分割接口

```bash
curl -X POST http://localhost:8107/internal/segment \
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
  "device": "cpu",
  "model_version": "LungNoduleSeg-v1.0"
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
| LUNG_NODULE_SEG_PORT | 8107 | 服务端口 |
| LUNG_NODULE_SEG_DEVICE | cpu（无CUDA时）| 推理设备 |
| LUNG_DATASET_ROOT | ~/Downloads/Task06_Lung | 训练数据根目录 |

## 注意事项

- `models/best_model.pth` 不入 git，训练完成后手动放置
- 推理在 Mac M2 CPU 上约 20-60 秒（取决于体数据大小），GPU 约 3-10 秒
- 风险分级为简化尺寸阈值规则（非严格 Lung-RADS），UI 中已注明"仅供参考"
