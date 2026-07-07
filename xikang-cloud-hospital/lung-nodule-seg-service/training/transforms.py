"""
数据预处理 Transforms（MONAI Compose）
=====================================

训练用 transforms  get_train_transforms()
验证用 transforms  get_val_transforms()

关键约束
--------
预处理流水线（前 4 步）的参数必须与推理服务 app/preprocess.py 保持完全一致：
  spacing   = SPACING_XYZ = (1.5, 1.5, 2.0) mm
  hu_min    = HU_MIN = -1000
  hu_max    = HU_MAX = 400
  orientation = "RAS"

任何改动都必须同步更新 app/preprocess.py。
"""

from __future__ import annotations

from monai.transforms import (
    Compose,
    EnsureChannelFirstd,
    LoadImaged,
    NormalizeIntensityd,
    Orientationd,
    RandAffined,
    RandCropByPosNegLabeld,
    RandFlipd,
    RandGaussianNoised,
    RandGaussianSmoothd,
    RandRotate90d,
    RandScaleIntensityd,
    ScaleIntensityRanged,
    Spacingd,
    ToTensord,
    CropForegroundd,
)

from . import config


def get_train_transforms() -> Compose:
    """
    训练用 Compose：
      1. 加载 NIfTI（image + label）
      2. 添加 channel 维度
      3. RAS 方向标准化
      4. 重采样到 SPACING_XYZ
      5. 肺窗 HU 归一化 → [0, 1]
      6. 裁剪前景（加速，减少纯背景区域）
      7. RandCropByPosNegLabeld（正负采样 patch，缓解类别不均衡）
      8. 数据增强

    crop_foreground 先去掉大片纯黑背景，然后 pos/neg crop 保证有前景 patch。
    """
    train_transforms = Compose(
        [
            # ---- 加载 ----
            LoadImaged(keys=["image", "label"]),
            EnsureChannelFirstd(keys=["image", "label"]),

            # ---- 预处理（与推理端一致） ----
            Orientationd(keys=["image", "label"], axcodes="RAS"),
            Spacingd(
                keys=["image", "label"],
                pixdim=config.SPACING_XYZ,
                mode=("bilinear", "nearest"),
            ),
            ScaleIntensityRanged(
                keys=["image"],
                a_min=config.HU_MIN,
                a_max=config.HU_MAX,
                b_min=0.0,
                b_max=1.0,
                clip=True,
            ),

            # ---- 前景裁剪：去除大块纯空气背景 ----
            CropForegroundd(
                keys=["image", "label"],
                source_key="image",
                margin=10,
            ),

            # ---- 正负采样（关键：缓解极端类别不均衡） ----
            RandCropByPosNegLabeld(
                keys=["image", "label"],
                label_key="label",
                spatial_size=config.PATCH_SIZE,
                pos=config.POS_NEG_RATIO,
                neg=1.0,
                num_samples=config.NUM_SAMPLES_PER_IMAGE,
                image_key="image",
                image_threshold=0.0,
            ),

            # ---- 数据增强 ----
            RandFlipd(keys=["image", "label"], prob=0.5, spatial_axis=0),
            RandFlipd(keys=["image", "label"], prob=0.5, spatial_axis=1),
            RandFlipd(keys=["image", "label"], prob=0.5, spatial_axis=2),
            RandRotate90d(keys=["image", "label"], prob=0.3, max_k=3),
            RandAffined(
                keys=["image", "label"],
                prob=0.2,
                rotate_range=(0.1, 0.1, 0.1),
                scale_range=(0.1, 0.1, 0.1),
                mode=("bilinear", "nearest"),
                padding_mode="border",
            ),
            RandGaussianNoised(keys=["image"], prob=0.15, mean=0.0, std=0.01),
            RandGaussianSmoothd(keys=["image"], prob=0.15, sigma_x=(0.5, 1.0)),
            RandScaleIntensityd(keys=["image"], factors=0.1, prob=0.2),

            # ---- 转 Tensor ----
            ToTensord(keys=["image", "label"]),
        ]
    )
    return train_transforms


def get_val_transforms() -> Compose:
    """
    验证用 Compose：确定性操作，不做随机裁剪和增强。
    验证时对整卷做滑窗推理，因此不需要 RandCropByPosNegLabeld。
    """
    val_transforms = Compose(
        [
            LoadImaged(keys=["image", "label"]),
            EnsureChannelFirstd(keys=["image", "label"]),
            Orientationd(keys=["image", "label"], axcodes="RAS"),
            Spacingd(
                keys=["image", "label"],
                pixdim=config.SPACING_XYZ,
                mode=("bilinear", "nearest"),
            ),
            ScaleIntensityRanged(
                keys=["image"],
                a_min=config.HU_MIN,
                a_max=config.HU_MAX,
                b_min=0.0,
                b_max=1.0,
                clip=True,
            ),
            ToTensord(keys=["image", "label"]),
        ]
    )
    return val_transforms
