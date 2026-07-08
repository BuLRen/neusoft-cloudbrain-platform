"""
训练配置
========

所有超参数和路径集中在此文件。
通过环境变量 LUNG_DATASET_ROOT 覆盖数据集路径。
"""

import os

# ========================
# 数据集路径
# ========================
DATA_ROOT = os.environ.get(
    "LUNG_DATASET_ROOT",
    os.path.expanduser("~/Downloads/Task06_Lung"),
)

IMAGES_DIR = os.path.join(DATA_ROOT, "imagesTr")
LABELS_DIR = os.path.join(DATA_ROOT, "labelsTr")
DATASET_JSON = os.path.join(DATA_ROOT, "dataset.json")

# ========================
# 划分
# ========================
VAL_FRACTION = 0.2   # 80/20 train/val 随机划分
RANDOM_SEED = 42

# ========================
# 预处理（必须与推理服务 app/preprocess.py 完全一致）
# ========================
SPACING_XYZ = (1.5, 1.5, 2.0)   # (x, y, z) mm；Spacingd pixdim = (1.5, 1.5, 2.0)
HU_MIN = -1000.0                  # 肺窗下限
HU_MAX = 400.0                    # 肺窗上限

# ========================
# patch 采样
# ========================
PATCH_SIZE = (96, 96, 96)         # Mac M2 可跑；GPU 上可改成 (128, 128, 128)
POS_NEG_RATIO = 1.0               # RandCropByPosNegLabel: pos:neg = 1:1
NUM_SAMPLES_PER_IMAGE = 2         # 每幅图每 epoch 采 N 个 patch
CACHE_NUM = 8                     # SmartCacheDataset 缓存数量（Mac 内存有限适当减小）

# ========================
# 网络
# ========================
IN_CHANNELS = 1
OUT_CHANNELS = 2                  # 0=背景, 1=肿瘤
MODEL_CHANNELS = (16, 32, 64, 128)
MODEL_STRIDES = (2, 2, 2)         # len = len(channels) - 1

# ========================
# 训练
# ========================
BATCH_SIZE = 2
MAX_EPOCHS = 50
VAL_INTERVAL = 5                  # 每 N 个 epoch 验证一次

LEARNING_RATE = 2e-4
WEIGHT_DECAY = 1e-5
LR_T_MAX = 50                     # CosineAnnealing T_max = MAX_EPOCHS

# ========================
# checkpoint
# ========================
CHECKPOINT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "models",
)
BEST_MODEL_FILENAME = "best_model.pth"
LAST_MODEL_FILENAME = "last_model.pth"

# ========================
# 验证推理（整卷滑窗）
# ========================
VAL_PATCH_SIZE = PATCH_SIZE
VAL_OVERLAP = 0.5
