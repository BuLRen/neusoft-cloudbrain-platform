"""
配置常量（DESIGN.md §6）
======================

所有可调参数集中在这里。修改阈值/尺寸只动本文件，不动业务代码。
"""

# ========================
# 服务
# ========================
SERVICE_HOST = "0.0.0.0"
SERVICE_PORT = 8105

# ========================
# 模型
# ========================
import os

# 服务根目录（main.py / uvicorn 都从服务根启动，相对路径以此为基准）
SERVICE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = os.path.join(SERVICE_ROOT, "models", "best_model.pth")
DEVICE = "cpu"                       # 服务器无 GPU
NUM_SEG_CLASSES = 5
NUM_CLS = 5

# 分割类别名（index 0..4）
# 0=背景, 1=金属, 2=线束硬化, 3=部分容积, 4=环形
ARTIFACT_CLASS_NAMES = [
    "background", "metal", "beam_hardening", "partial_volume", "ring"
]

# cls 向量语义（5 维）
# [0]=有无伪影, [1]=金属, [2]=线束硬化, [3]=部分容积, [4]=环形
# artifact_types 输出键（对应 cls[1..4]）
CLS_ARTIFACT_KEYS = ["metal", "beam_hardening", "partial_volume", "ring"]

# ========================
# 预处理（必须与 train/transform.py 的 common_pre 完全一致，不可改）
# ========================
HU_MIN, HU_MAX = -80.0, 80.0         # 脑窗
SPACING = (2.0, 2.0, 2.5)            # (x, y, z) mm —— 注意 monai Spacingd pixdim 也是 (x,y,z)
PATCH_SIZE = (32, 224, 224)          # (D, H, W)
OVERLAP = 0.5                        # 滑窗重叠率 → 步长 = patch // 2

# ========================
# 后处理阈值
# ========================
CLS_THRESHOLD = 0.5                  # cls[0] sigmoid > 此值 → has_artifact=True

# 严重程度映射（按伪影体素占比）
# 从小到大遍历，ratio >= 阈值即取该档；列表必须升序
SEVERITY_THRESHOLDS = [
    (0.00, "clean"),      # ratio == 0
    (0.001, "mild"),      # 0 < ratio < 0.5%
    (0.02, "moderate"),   # 0.5% <= ratio < 2%
    (0.05, "severe"),     # ratio >= 2% → 重扫建议
]

# ========================
# 并发（CPU 保护）
# ========================
MAX_CONCURRENT_INFERENCES = 1        # 同时只跑一个推理
