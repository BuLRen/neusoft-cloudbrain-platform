"""DICOM / 体数据读写与元信息序列化（无状态，按路径读写）。"""

from __future__ import annotations

import os
from typing import Tuple

import numpy as np
import SimpleITK as sitk


def read_dicom_series(dcm_folder: str) -> Tuple[sitk.Image, str, int]:
    """
    读取 DICOM 文件夹；若存在多个 Series，选择切片数量最多的序列。
    """
    reader = sitk.ImageSeriesReader()
    series_ids = reader.GetGDCMSeriesIDs(dcm_folder)
    if not series_ids:
        raise RuntimeError("当前文件夹中没有找到有效的 DICOM 序列。")

    best_series_id = None
    best_file_names: list[str] = []
    for sid in series_ids:
        file_names = reader.GetGDCMSeriesFileNames(dcm_folder, sid)
        if len(file_names) > len(best_file_names):
            best_file_names = file_names
            best_series_id = sid

    reader.SetFileNames(best_file_names)
    image = reader.Execute()
    return image, best_series_id or "", len(best_file_names)


def read_volume_file(file_path: str) -> sitk.Image:
    """读取 NRRD / NIfTI 单文件。"""
    if not os.path.isfile(file_path):
        raise RuntimeError(f"文件不存在：{file_path}")
    return sitk.ReadImage(file_path)


def write_image_to_nrrd(image: sitk.Image, out_path: str) -> None:
    """将 SimpleITK 图像写入 NRRD 文件。"""
    parent = os.path.dirname(out_path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    sitk.WriteImage(image, out_path)


def image_meta(
    image: sitk.Image,
    *,
    source_name: str = "",
    is_mask: bool = False,
    series_id: str = "",
    file_count: int = 0,
) -> dict:
    """返回前端所需的体数据元信息。"""
    arr = sitk.GetArrayFromImage(image)
    size = image.GetSize()
    spacing = image.GetSpacing()
    return {
        "shape_zyx": list(arr.shape),
        "size_xyz": [int(size[0]), int(size[1]), int(size[2])],
        "spacing_xyz": [float(spacing[0]), float(spacing[1]), float(spacing[2])],
        "min": float(np.min(arr)),
        "max": float(np.max(arr)),
        "is_mask": bool(is_mask),
        "source_name": source_name,
        "series_id": series_id,
        "file_count": int(file_count),
    }
