"""
NRRD 读取 + 预处理（SimpleITK 实现）
=====================================

预处理流水线与 training/transforms.py 的确定性部分完全一致：
  1. SimpleITK 读取 NRRD（或 NIfTI）
  2. 方向标准化到 RAS
  3. 重采样到 SPACING_XYZ = (1.5, 1.5, 2.0) mm，双线性插值
  4. 肺窗 HU 归一化 [-1000, 400] → [0, 1]，clip=True

SimpleITK 轴序约定：
  - GetSpacing() 返回 (x, y, z)
  - GetArrayFromImage() 返回 numpy (z, y, x) = (D, H, W)
  - 本模块统一在 numpy (D, H, W) 空间操作

输出：
  volume_tensor  : (1, 1, D, H, W) float32 tensor，直接喂推理
  sitk_image     : 重采样后的 sitk.Image，供后处理计算世界坐标用
"""

from __future__ import annotations

import numpy as np
import SimpleITK as sitk
import torch

from . import config


def _to_ras(image: sitk.Image) -> sitk.Image:
    return sitk.DICOMOrient(image, "RAS")


def _resample(image: sitk.Image, is_label: bool = False) -> sitk.Image:
    """
    重采样到 config.SPACING_XYZ。
    is_label=True 时用最近邻插值（保持标签整数值）。
    """
    target_spacing = tuple(float(s) for s in config.SPACING_XYZ)
    orig_spacing = image.GetSpacing()
    orig_size = image.GetSize()

    new_size = [
        int(round(os_ * sp / ts))
        for os_, sp, ts in zip(orig_size, orig_spacing, target_spacing)
    ]

    resampler = sitk.ResampleImageFilter()
    resampler.SetOutputSpacing(target_spacing)
    resampler.SetSize(new_size)
    resampler.SetOutputDirection(image.GetDirection())
    resampler.SetOutputOrigin(image.GetOrigin())
    resampler.SetTransform(sitk.Transform())
    resampler.SetInterpolator(
        sitk.sitkNearestNeighbor if is_label else sitk.sitkLinear
    )
    resampler.SetOutputPixelType(image.GetPixelID())
    return resampler.Execute(image)


def _lung_window_normalize(arr: np.ndarray) -> np.ndarray:
    arr = arr.astype(np.float32)
    lo, hi = float(config.HU_MIN), float(config.HU_MAX)
    arr = (arr - lo) / (hi - lo)
    np.clip(arr, 0.0, 1.0, out=arr)
    return arr


def load_and_preprocess(file_path: str) -> tuple[torch.Tensor, sitk.Image]:
    """
    读取 NRRD/NIfTI 文件，预处理后返回模型输入张量和重采样后的 sitk.Image。

    参数
    ----
    file_path : str
        .nrrd / .nii / .nii.gz 文件路径

    返回
    ----
    (volume_tensor, resampled_image)
    volume_tensor : (1, 1, D, H, W) float32
    resampled_image : sitk.Image（RAS 方向，统一 spacing），用于后处理计算坐标
    """
    try:
        image = sitk.ReadImage(file_path)
        image = _to_ras(image)
        image = _resample(image, is_label=False)

        arr = sitk.GetArrayFromImage(image)  # (D, H, W)
        arr = _lung_window_normalize(arr)

        tensor = torch.from_numpy(arr).to(torch.float32)
        tensor = tensor.unsqueeze(0).unsqueeze(0).contiguous()  # (1, 1, D, H, W)
        return tensor, image
    except Exception as e:
        raise RuntimeError(f"预处理失败: {type(e).__name__}: {e}") from e


def get_original_hu_array(file_path: str) -> tuple[np.ndarray, sitk.Image]:
    """
    读取 NRRD 并重采样到标准间距，返回原始 HU 值数组（未归一化），
    供后处理计算病灶平均 HU 密度。
    """
    try:
        image = sitk.ReadImage(file_path)
        image = _to_ras(image)
        image = _resample(image, is_label=False)
        arr = sitk.GetArrayFromImage(image).astype(np.float32)  # (D, H, W), 原始 HU
        return arr, image
    except Exception as e:
        raise RuntimeError(f"读取原始 HU 数组失败: {e}") from e
