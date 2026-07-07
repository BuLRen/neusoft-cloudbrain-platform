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
SERVICE_PORT = int(os.environ.get("LUNG_NODULE_SEG_PORT", 8107))

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
# 并发
# ========================
MAX_CONCURRENT_INFERENCES = 1
