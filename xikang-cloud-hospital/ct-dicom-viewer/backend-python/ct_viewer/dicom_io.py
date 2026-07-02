"""DICOM / 体数据读写与元信息序列化。"""

from __future__ import annotations

import os
import tempfile
from typing import Tuple

import numpy as np
import SimpleITK as sitk

from .volume_store import VolumeRecord


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


def serialize_image_to_nrrd_bytes(image: sitk.Image) -> bytes:
    """将 SimpleITK 图像序列化为 NRRD 二进制（供前端 vtk.js 解析）。"""
    with tempfile.NamedTemporaryFile(suffix=".nrrd", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        sitk.WriteImage(image, tmp_path)
        with open(tmp_path, "rb") as f:
            return f.read()
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def image_meta(image: sitk.Image, record: VolumeRecord) -> dict:
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
        "is_mask": bool(record.is_mask),
        "source_name": record.source_name,
        "series_id": record.series_id,
        "file_count": int(record.file_count),
    }
