"""
服务配置常量
============

所有可调参数集中在此文件，业务代码不直接用 magic number。
预处理参数必须与 training/config.py 保持完全一致。
"""

import os

# ========================
# 服务
# ========================
SERVICE_HOST = "0.0.0.0"
SERVICE_PORT = int(os.environ.get("LUNG_NODULE_SEG_PORT", 8222))

# ========================
# 模型权重
# ========================
SERVICE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(SERVICE_ROOT, "models", "best_model.pth")

# 设备：mac 上跑 cpu（MPS 对 3D 卷积支持有限），GPU 机器自动 cuda
import torch

def _select_device() -> str:
    if torch.cuda.is_available():
        return "cuda"
    # MPS 对推理 3D 卷积（MONAI UNet）存在不稳定问题，默认 cpu 保稳
    return "cpu"

DEVICE = os.environ.get("LUNG_NODULE_SEG_DEVICE", _select_device())

# ========================
# 网络（必须与 training/config.py 一致）
# ========================
IN_CHANNELS = 1
OUT_CHANNELS = 2           # 0=背景, 1=肿瘤
MODEL_CHANNELS = (16, 32, 64, 128)
MODEL_STRIDES = (2, 2, 2)

# ========================
# 预处理（必须与 training/transforms.py 一致）
# ========================
SPACING_XYZ = (1.5, 1.5, 2.0)    # (x, y, z) mm
HU_MIN = -1000.0
HU_MAX = 400.0

# ========================
# 滑窗推理
# ========================
PATCH_SIZE = (96, 96, 96)
OVERLAP = 0.5

# ========================
# 后处理
# ========================
SEG_THRESHOLD = 0.5               # sigmoid 概率阈值，超过则判为前景
MIN_LESION_VOXELS = 10            # 连通域最小体素数（过滤噪点）
MAX_LESIONS = 20                  # 最多报告 N 个病灶（按体积降序）

# 风险分级（按最大径，mm）—— 简化规则，非 Lung-RADS 临床标准
RISK_LEVELS = [
    (0.0,  "低风险"),   # 直径 < 6mm
    (6.0,  "中风险"),   # 6mm <= 直径 < 15mm
    (15.0, "高风险"),   # 直径 >= 15mm
]

MODEL_VERSION = "LungNoduleSeg-v1.0"

# ========================
# 后处理参数按 backend 覆盖
# ========================
# segnet 是逐 2D 切片独立推理（无 3D 上下文约束），血管截面、支气管壁、钙化灶
# 等结构很容易被误判为一个个孤立的小前景区域；若沿用整卷 3D 模型（monai/
# nnunet）的过滤阈值，单个 CT 常检出 8~10+ 个"病灶"，明显偏多且与该开源
# demo（通常只有 1~2 个明显病灶）不符。这里对 segnet 单独收紧规则：
#   - min_volume_mm3    ：按物理体积过滤（不受层厚/像素间距影响），滤掉细碎噪点
#   - min_slice_span    ：病灶至少跨 2 个轴位切片，滤掉只在单层出现的伪影
#   - min_confidence    ：区域内平均预测概率过低视为噪声
#   - morph_opening_radius：连通域分析前先做一次形态学开运算去孤立噪点
# 详见 postprocess.extract_lesions 的对应参数说明。
POSTPROCESS_OVERRIDES = {
    "segnet": {
        "min_volume_mm3": 300.0,
        "min_slice_span": 2,
        "min_confidence": 0.6,
        "morph_opening_radius": 1,
    },
}

# ========================
# 推理后端选择
# ========================
# "monai"  ：本仓库 training/ 自训练的轻量 3D UNet（默认，与 MODEL_PATH 配套）
# "segnet" ：移植自 Ola-Vish/lung-tumor-segmentation 的 2D SegNet（轻量演示模型）
# "nnunet" ：官方 nnU-Net v2 框架推理（更大网络、更长训练，需额外安装
#            requirements-nnunet.txt 并通过 scripts/download_nnunet_weights.py
#            下载权重）
#
# 注意：从本版本开始，服务在启动时会尝试加载全部已配置权重的模型（见下方
# “多模型注册表”），供前端在运行时通过 model_id 字段任选一个，而不再要求
# 重启服务切换后端。LUNG_NODULE_SEG_BACKEND 仅决定“默认模型”（未显式传
# model_id 时使用哪一个），兼容旧版单后端部署方式。
ACTIVE_BACKEND = os.environ.get("LUNG_NODULE_SEG_BACKEND", "monai").strip().lower()
if ACTIVE_BACKEND not in ("monai", "segnet", "nnunet"):
    raise ValueError(
        f"不支持的 LUNG_NODULE_SEG_BACKEND={ACTIVE_BACKEND!r}，只能是 'monai' / 'segnet' / 'nnunet'"
    )

# ========================
# nnU-Net 后端配置
# ========================
# nnUNet_results 目录，结构需为：
#   {NNUNET_RESULTS_DIR}/{NNUNET_DATASET_NAME}/
#     {NNUNET_TRAINER}__{NNUNET_PLANS}__{NNUNET_CONFIGURATION}/
#       dataset.json, plans.json, dataset_fingerprint.json
#       fold_{NNUNET_FOLD}/{NNUNET_CHECKPOINT}
# 参考 scripts/download_nnunet_weights.py 自动生成该结构。
NNUNET_RESULTS_DIR = os.environ.get(
    "NNUNET_RESULTS_DIR",
    os.path.join(SERVICE_ROOT, "models", "nnunet_results"),
)
NNUNET_DATASET_NAME = os.environ.get("NNUNET_DATASET_NAME", "Dataset502_MSDLung")
NNUNET_TRAINER = os.environ.get("NNUNET_TRAINER", "nnUNetTrainer")
NNUNET_PLANS = os.environ.get("NNUNET_PLANS", "nnUNetPlans")
NNUNET_CONFIGURATION = os.environ.get("NNUNET_CONFIGURATION", "3d_fullres")
NNUNET_FOLD = int(os.environ.get("NNUNET_FOLD", "0"))
NNUNET_CHECKPOINT = os.environ.get("NNUNET_CHECKPOINT", "checkpoint_best.pth")
# 镜像 TTA：精度更高但推理慢约 8 倍，CPU/MPS 上建议关闭
NNUNET_USE_MIRRORING = os.environ.get("NNUNET_USE_MIRRORING", "0") not in ("0", "false", "False", "")
NNUNET_STEP_SIZE = float(os.environ.get("NNUNET_STEP_SIZE", "0.5"))
# 低内存模式：默认不要求 nnU-Net 返回整幅 softmax 概率图，只保留最终分割。
# 这会让病灶 confidence 字段退化为近似值，但能明显降低 CPU 推理峰值内存。
NNUNET_RETURN_PROBABILITIES = os.environ.get("NNUNET_RETURN_PROBABILITIES", "0") not in (
    "0", "false", "False", ""
)
NNUNET_MODEL_VERSION = os.environ.get(
    "NNUNET_MODEL_VERSION", "LungNoduleSeg-nnUNet-Task06Lung-fold0"
)

# 是否启用 nnunet 后端（默认关闭：依赖体积大且默认不推荐使用）。
# 设置 LUNG_NODULE_SEG_BACKEND=nnunet 会自动启用；也可单独用该变量开启。
ENABLE_NNUNET = os.environ.get(
    "LUNG_NODULE_SEG_ENABLE_NNUNET",
    "1" if ACTIVE_BACKEND == "nnunet" else "0",
) not in ("0", "false", "False", "")

# 推理结束后主动释放 Python 大数组；CPU 推理场景下有助于降低下一次请求前的驻留内存。
FORCE_GC_AFTER_INFERENCE = os.environ.get("LUNG_NODULE_SEG_FORCE_GC", "1") not in (
    "0", "false", "False", ""
)


def _select_nnunet_device() -> str:
    if torch.cuda.is_available():
        return "cuda"
    # nnU-Net 官方代码只针对 cuda 做了 perform_everything_on_device 优化，
    # mps 支持不完善，稳妥起见默认退回 cpu（可用环境变量强制指定）。
    return "cpu"


NNUNET_DEVICE = os.environ.get("LUNG_NODULE_SEG_NNUNET_DEVICE", _select_nnunet_device())

# ========================
# SegNet 后端配置
# ========================
# 移植自 Ola-Vish/lung-tumor-segmentation（MIT License，
# https://github.com/Ola-Vish/lung-tumor-segmentation）。原仓库在 MSD
# Task06_Lung 上训练了一个 VGG16-BN 编码器的 2D SegNet，逐轴位切片推理，
# 网络体积远小于 nnU-Net，CPU 上跑得动，适合低配设备演示。
#
# 权重获取方式（无官方 API，需手动下载 + 转换，见 README「SegNet 后端」一节）：
#   1. 从原仓库 README 中的 Google Drive 链接下载作者提供的 checkpoint
#   2. python -m scripts.convert_segnet_checkpoint --src <下载的 .ckpt>
#   3. 转换脚本默认输出到下面的 SEGNET_MODEL_PATH
SEGNET_MODEL_PATH = os.environ.get(
    "SEGNET_MODEL_PATH",
    os.path.join(SERVICE_ROOT, "models", "segnet_lung_tumor.pth"),
)
SEGNET_INPUT_SIZE = (224, 224)      # (H, W)，与原仓库训练分辨率一致，不可更改
SEGNET_SCALING_VALUE = 3071.0       # 原仓库预处理：CT 原始值 / 3071（不做 clip）
SEGNET_BATCH_SIZE = int(os.environ.get("SEGNET_BATCH_SIZE", "4"))
SEGNET_MODEL_VERSION = os.environ.get(
    "SEGNET_MODEL_VERSION", "LungTumorSeg-SegNet-OlaVish-v1"
)
SEGNET_DEVICE = os.environ.get("LUNG_NODULE_SEG_SEGNET_DEVICE", DEVICE)

# Z 轴平滑参数（仅影响 SegNet 逐切片 2D 推理的后处理）
# SegNet 是 2D 模型，每张切片独立推理无 3D 上下文约束，相邻切片预测概率可能
# 忽高忽低，导致 3D 掩码仅 1~2 层厚，在冠状/矢状图中呈扁平薄片状。
# 对 prob_map 在 Z 轴做 Gaussian 平滑（sigma 单位为切片数）可以将检测到的
# 高概率区域连续传播到邻近切片，使病灶 3D 形态更立体、更接近真实肿瘤形状。
#
# SEGNET_Z_SMOOTH_SIGMA : Gaussian 标准差（切片数），建议 1.0~2.5。
#   值越大：病灶在 Z 方向越"厚"，适合大肿瘤；但若过大，极薄层面噪点也会被放大。
#   设为 0 关闭平滑。
# SEGNET_PROB_THRESHOLD : 平滑后的二值化阈值，应低于原始 0.5，因为平滑会稀释
#   单层面的峰值概率。建议 0.30~0.40。
SEGNET_Z_SMOOTH_SIGMA = float(os.environ.get("SEGNET_Z_SMOOTH_SIGMA", "1.5"))
SEGNET_PROB_THRESHOLD = float(os.environ.get("SEGNET_PROB_THRESHOLD", "0.35"))

# ========================
# 多模型注册表
# ========================
# 供 /health 的 available_models 与 /internal/segment 的 model_id 字段使用。
# 服务启动时会尝试加载下列全部模型（nnunet 除外，需 ENABLE_NNUNET=1 才尝试），
# 加载失败不影响服务启动，只是该模型在 available_models 中 loaded=false。
DEFAULT_MODEL_ID = os.environ.get("LUNG_NODULE_SEG_DEFAULT_MODEL", ACTIVE_BACKEND).strip().lower()
if DEFAULT_MODEL_ID == "nnunet":
    ENABLE_NNUNET = True

MODEL_REGISTRY_META = {
    "monai": {
        "label": "MONAI 3D UNet（自训练，默认）",
        "description": "本仓库 training/ 自训练的轻量 3D UNet，整卷滑窗推理。",
    },
    "segnet": {
        "label": "SegNet 2D（VGG16 编码器，轻量演示模型）",
        "description": (
            "移植自 Ola-Vish/lung-tumor-segmentation（MIT License），逐轴位切片 "
            "2D 推理，模型体积小、CPU 推理快，适合设备资源有限时的演示场景。"
        ),
    },
    "nnunet": {
        "label": "nnU-Net v2（体积较大，默认不启用）",
        "description": "官方 nnU-Net v2 框架权重，精度更高但网络更大、CPU 推理慢，需要额外安装依赖。",
    },
}

# 当前默认模型的版本号（写入 summary.modelVersion / health 接口的兜底值）
_DEFAULT_VERSION_BY_MODEL = {
    "monai": MODEL_VERSION,
    "segnet": SEGNET_MODEL_VERSION,
    "nnunet": NNUNET_MODEL_VERSION,
}
ACTIVE_MODEL_VERSION = _DEFAULT_VERSION_BY_MODEL.get(DEFAULT_MODEL_ID, MODEL_VERSION)

# ========================
# 并发
# ========================
MAX_CONCURRENT_INFERENCES = 1
