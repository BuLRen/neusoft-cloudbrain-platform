"""
NIfTI 读取 + 预处理（SimpleITK 实现）
=====================================

为什么不用 monai：
  monai 1.6 的 transforms API（LoadImage/EnsureChannelFirst/Spacing 等）
  和早期版本变动较大，单图像版（无 d 后缀）在 NIfTI 上的行为不稳定，
  调试成本高。改用 SimpleITK 手写预处理，每一步参数完全可控，且不依赖
  monai 版本。

预处理流水线（语义与训练 train/transform.py 的 common_pre 完全一致）：
  1. SimpleITK 读 NIfTI          → 原始体数据 + spacing/direction
  2. 方向标准化到 RAS            → DIBCO 约定，z,y,x 轴顺序固定
  3. 重采样到 spacing (2.0, 2.0, 2.5) mm  ← (x, y, z)，bilinear
  4. 脑窗 HU 归一化 (-80, 80) → (0, 1)，clip=True

输出张量形状：(1, 1, D, H, W)，float32，可直接喂模型做滑窗推理。

注意 SimpleITK 的轴顺序约定：
  - sitk Image 的 GetSpacing() 返回 (x, y, z)
  - sitk.GetArrayFromImage() 返回的 numpy 数组是 (z, y, x) = (D, H, W)
  - 本模块统一在 numpy (D, H, W) 空间操作，最后加 channel + batch 维
"""

from __future__ import annotations

import numpy as np
import SimpleITK as sitk
import torch

from . import config


def _to_ras(image: sitk.Image) -> sitk.Image:
    """
    把图像方向标准化到 RAS（Right-Anterior-Superior）。

    monai Orientation(axcodes="RAS") 的等价实现：
      用 sitk.DICOMOrient(image, "RAS") 直接得到 RAS 方向的图像。
    """
    return sitk.DICOMOrient(image, "RAS")


def _resample(image: sitk.Image) -> sitk.Image:
    """
    重采样到 config.SPACING = (2.0, 2.0, 2.5) mm (x, y, z)。

    训练 transform.py 的 Spacingd(pixdim=(2.0, 2.0, 2.5), mode="bilinear") 等价实现：
      图像用 sitk.sitkLinear（线性插值，等价 bilinear）。
    """
    target_spacing = (float(config.SPACING[0]),
                      float(config.SPACING[1]),
                      float(config.SPACING[2]))  # (x, y, z)

    original_spacing = image.GetSpacing()
    original_size = image.GetSize()

    # 按比例计算重采样后的 size（x, y, z 顺序）
    new_size = [
        int(round(orig_sz * orig_sp / tgt_sp))
        for orig_sz, orig_sp, tgt_sp in zip(original_size, original_spacing, target_spacing)
    ]

    resampler = sitk.ResampleImageFilter()
    resampler.SetOutputSpacing(target_spacing)
    resampler.SetSize(new_size)
    resampler.SetOutputDirection(image.GetDirection())
    resampler.SetOutputOrigin(image.GetOrigin())
    resampler.SetTransform(sitk.Transform())
    resampler.SetInterpolator(sitk.sitkLinear)  # bilinear
    # 保持原始像素类型，避免精度丢失
    resampler.SetOutputPixelType(image.GetPixelID())
    return resampler.Execute(image)


def _brain_window_normalize(arr: np.ndarray) -> np.ndarray:
    """
    脑窗 HU 归一化：(-80, 80) → (0, 1)，clip=True。

    等价于 monai ScaleIntensityRanged(a_min=-80, a_max=80, b_min=0, b_max=1, clip=True)：
      - 先把 [-80, 80] 线性映射到 [0, 1]
      - 超出范围的值裁剪到 [0, 1]
    """
    arr = arr.astype(np.float32, copy=True)
    a_min, a_max = float(config.HU_MIN), float(config.HU_MAX)
    # 线性映射：(x - a_min) / (a_max - a_min)
    arr = (arr - a_min) / (a_max - a_min)
    # clip 到 [0, 1]
    np.clip(arr, 0.0, 1.0, out=arr)
    return arr


def load_and_preprocess(file_path: str) -> torch.Tensor:
    """
    读取 NIfTI 文件并完成预处理，返回模型输入张量。

    参数
    ----
    file_path : str
        .nii 或 .nii.gz 文件路径

    返回
    ----
    torch.Tensor, shape=(1, 1, D, H, W), dtype=float32
        预处理后的体数据，可直接喂给模型做滑窗推理

    异常
    ----
    读取/解析失败时抛 RuntimeError，由 main.py 捕获并转成 4221 错误码。
    """
    try:
        # 1. 读取 NIfTI
        image = sitk.ReadImage(file_path)

        # 2. 方向标准化到 RAS
        image = _to_ras(image)

        # 3. 重采样到 (2.0, 2.0, 2.5) mm
        image = _resample(image)

        # 4. 取数组 (D, H, W)（sitk 数组顺序是 z, y, x）
        arr = sitk.GetArrayFromImage(image)  # (D, H, W)

        # 5. 脑窗 HU 归一化
        arr = _brain_window_normalize(arr)

    except Exception as e:  # noqa: BLE001 —— 任何解析错误都接住
        raise RuntimeError(f"NIfTI 解析失败: {type(e).__name__}: {e}") from e

    # 6. 转 tensor：(D,H,W) → (1, D, H, W) [channel] → (1, 1, D, H, W) [batch]
    tensor = torch.from_numpy(arr).to(torch.float32)        # (D, H, W)
    tensor = tensor.unsqueeze(0).unsqueeze(0).contiguous()  # (1, 1, D, H, W)
    return tensor
