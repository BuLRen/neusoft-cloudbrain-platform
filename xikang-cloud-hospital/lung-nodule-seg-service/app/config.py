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
# 推理后端选择
# ========================
# "monai"  ：本仓库 training/ 自训练的轻量 3D UNet（默认，与 MODEL_PATH 配套）
# "nnunet" ：官方 nnU-Net v2 框架推理（更大网络、更长训练，需额外安装
#            requirements-nnunet.txt 并通过 scripts/download_nnunet_weights.py
#            下载权重）
ACTIVE_BACKEND = os.environ.get("LUNG_NODULE_SEG_BACKEND", "monai").strip().lower()
if ACTIVE_BACKEND not in ("monai", "nnunet"):
    raise ValueError(
        f"不支持的 LUNG_NODULE_SEG_BACKEND={ACTIVE_BACKEND!r}，只能是 'monai' 或 'nnunet'"
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

# 当前生效的模型版本号（写入 summary.modelVersion / health 接口）
ACTIVE_MODEL_VERSION = NNUNET_MODEL_VERSION if ACTIVE_BACKEND == "nnunet" else MODEL_VERSION

# ========================
# 并发
# ========================
MAX_CONCURRENT_INFERENCES = 1
