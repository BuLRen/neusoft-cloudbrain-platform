"""
连通域分析与病灶指标计算
========================

输入：
  binary_mask  : (D, H, W) uint8，0=背景, 1=肿瘤（由推理模块输出）
  prob_map     : (D, H, W) float32，每体素的肿瘤预测概率；低内存模式下可为 None
  hu_arr       : (D, H, W) float32，原始 HU 值（未归一化，供密度计算）
  sitk_image   : sitk.Image（RAS 方向，统一间距），用于转世界坐标

每个连通域（病灶实例）计算：
  - centroidXyz : [x, y, z] 世界坐标（mm），用于前端定位
  - sliceIndex  : 轴状切片索引（centroid 的 D 轴索引）
  - plane       : "axial"
  - bbox        : [d_min, h_min, w_min, d_max, h_max, w_max]（体素坐标）
  - diameterMm  : 病灶最大径（mm），用 bbox 三轴中最长边 × spacing 近似
  - volumeMm3   : 体积（体素数 × 体素体积 mm³）
  - volumeCm3   : volumeMm3 / 1000
  - meanDensityHU : 病灶区域内原始 CT HU 均值
  - confidence  : 病灶区域内预测概率均值
  - riskLevel   : "低风险" | "中风险" | "高风险"（简化规则）
  - source      : "deep_learning"

Summary（全局汇总）：
  - lesionCount
  - maxDiameterMm
  - totalVolumeMm3
  - totalVolumeCm3
  - overallRiskLevel（取最高风险等级）
  - modelVersion
  - processingTimeMs（由调用方传入）
"""

from __future__ import annotations

import math
from typing import Any, Dict, List, Tuple

import numpy as np
import SimpleITK as sitk

try:
    from skimage.measure import label as cc_label, regionprops
    _SKIMAGE_AVAILABLE = True
except ImportError:
    _SKIMAGE_AVAILABLE = False

from . import config


def _risk_level(diameter_mm: float) -> str:
    level = config.RISK_LEVELS[0][1]
    for threshold, name in config.RISK_LEVELS:
        if diameter_mm >= threshold:
            level = name
    return level


def _overall_risk(lesion_list: List[Dict[str, Any]]) -> str:
    if not lesion_list:
        return "低风险"
    risk_order = {name: idx for idx, (_, name) in enumerate(config.RISK_LEVELS)}
    max_idx = max(risk_order.get(l.get("riskLevel", "低风险"), 0) for l in lesion_list)
    return config.RISK_LEVELS[max_idx][1]


def _voxel_to_world(
    sitk_image: sitk.Image,
    d: float, h: float, w: float,
) -> List[float]:
    """
    将体素坐标 (d, h, w)（即 z, y, x）转换为世界坐标 (x, y, z) mm。
    sitk TransformIndexToPhysicalPoint 接受 (x, y, z) 顺序。
    """
    pt = sitk_image.TransformIndexToPhysicalPoint((int(w), int(h), int(d)))
    return [round(float(pt[0]), 2), round(float(pt[1]), 2), round(float(pt[2]), 2)]


def _compute_lesion_metrics(
    region,              # skimage regionprops region 对象
    prob_map: np.ndarray | None,
    hu_arr: np.ndarray,
    sitk_image: sitk.Image,
    spacing_dhw: Tuple[float, float, float],
    lesion_id: int,
) -> Dict[str, Any]:
    """
    计算单个连通域的所有指标。

    spacing_dhw : (spacing_d, spacing_h, spacing_w)，单位 mm
                  对应 (z, y, x) 轴，由 sitk spacing (x,y,z) 转换：
                  spacing_dhw = (spacing_z, spacing_y, spacing_x)
    """
    sd, sh, sw = spacing_dhw

    # ---- 质心 ----
    centroid_dhw = region.centroid  # (d, h, w) float
    centroid_xyz = _voxel_to_world(sitk_image, *centroid_dhw)

    # ---- bbox ----
    d_min, h_min, w_min, d_max, h_max, w_max = region.bbox  # 不含 max 端

    # ---- 最大径：bbox 三轴中最长边 × 对应 spacing ----
    len_d = (d_max - d_min) * sd
    len_h = (h_max - h_min) * sh
    len_w = (w_max - w_min) * sw
    diameter_mm = round(max(len_d, len_h, len_w), 2)

    # ---- 体积 ----
    voxel_vol_mm3 = sd * sh * sw
    volume_mm3 = round(region.num_pixels * voxel_vol_mm3, 2)
    volume_cm3 = round(volume_mm3 / 1000.0, 4)

    # ---- 平均 HU 密度 / 置信度 ----
    # 避免 region.coords 生成 (N, 3) 巨型坐标矩阵；大连通域时可节省大量内存。
    region_mask = region.image
    region_slices = (
        slice(d_min, d_max),
        slice(h_min, h_max),
        slice(w_min, w_max),
    )
    mean_hu = float(np.mean(hu_arr[region_slices], where=region_mask))
    mean_hu = round(mean_hu, 1)

    if prob_map is not None:
        confidence = float(np.mean(prob_map[region_slices], where=region_mask))
    else:
        # nnU-Net 低内存模式不返回整幅概率图，用二值命中近似置信度。
        confidence = 1.0
    confidence = round(confidence, 4)

    # ---- 风险分级 ----
    risk_level = _risk_level(diameter_mm)

    return {
        "id": lesion_id,
        "label": f"病灶 {lesion_id}",
        "sliceIndex": int(round(centroid_dhw[0])),
        "plane": "axial",
        "centroidXyz": centroid_xyz,
        "diameterMm": diameter_mm,
        "bbox": [d_min, h_min, w_min, d_max, h_max, w_max],
        "volumeMm3": volume_mm3,
        "volumeCm3": volume_cm3,
        "meanDensityHU": mean_hu,
        "confidence": confidence,
        "riskLevel": risk_level,
        "source": "deep_learning",
    }


def _cc_fallback(binary_mask: np.ndarray) -> np.ndarray:
    """
    当 skimage 不可用时，用 numpy 基础实现简单的连通域标注（仅用于单连通情况降级）。
    建议安装 scikit-image 以获取完整功能。
    """
    labeled = np.zeros_like(binary_mask, dtype=np.int32)
    if binary_mask.sum() > 0:
        labeled[binary_mask > 0] = 1
    return labeled


def extract_lesions(
    binary_mask: np.ndarray,
    prob_map: np.ndarray | None,
    hu_arr: np.ndarray,
    sitk_image: sitk.Image,
    processing_time_ms: int = 0,
    model_version: str | None = None,
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    """
    从二值掩码中提取独立病灶实例，计算各项指标。

    参数
    ----
    binary_mask      : (D, H, W) uint8，0=背景, 1=肿瘤
    prob_map         : (D, H, W) float32，肿瘤预测概率；低内存模式下可为 None
    hu_arr           : (D, H, W) float32，原始 HU 值
    sitk_image       : 重采样后的 sitk.Image（RAS），提供 spacing 与坐标转换
    processing_time_ms : 推理耗时（毫秒）

    返回
    ----
    (lesions, summary)
    lesions  : List[dict]，每个病灶的指标字典（按体积降序排列，最多 MAX_LESIONS 个）
    summary  : dict，全局汇总
    """
    # ---- spacing 转换：sitk (x, y, z) → dhw = (z, y, x) ----
    sitk_spacing_xyz = sitk_image.GetSpacing()   # (sx, sy, sz)
    spacing_dhw = (
        float(sitk_spacing_xyz[2]),   # z → d
        float(sitk_spacing_xyz[1]),   # y → h
        float(sitk_spacing_xyz[0]),   # x → w
    )

    # ---- 连通域标注 ----
    if _SKIMAGE_AVAILABLE:
        labeled = cc_label(binary_mask)
        props = regionprops(labeled)
    else:
        labeled = _cc_fallback(binary_mask)
        # 无 skimage 时只能做极简处理
        props = []
        if labeled.max() > 0:
            class _FakeRegion:
                num_pixels = int(binary_mask.sum())
                centroid = tuple(float(c) for c in np.array(np.where(binary_mask > 0)).mean(axis=1))
                coords = np.array(np.where(binary_mask > 0)).T
                bbox = (
                    int(coords[:, 0].min()), int(coords[:, 1].min()), int(coords[:, 2].min()),
                    int(coords[:, 0].max() + 1), int(coords[:, 1].max() + 1), int(coords[:, 2].max() + 1),
                )
            props = [_FakeRegion()]

    # ---- 过滤噪点 + 计算指标 ----
    lesions = []
    for region in props:
        if region.num_pixels < config.MIN_LESION_VOXELS:
            continue
        try:
            lesion = _compute_lesion_metrics(
                region, prob_map, hu_arr, sitk_image, spacing_dhw,
                lesion_id=len(lesions) + 1,
            )
            lesions.append(lesion)
        except Exception as e:
            pass  # 单个病灶计算失败不影响其余结果

    # 按体积降序排列，取最多 MAX_LESIONS 个
    lesions.sort(key=lambda x: x["volumeMm3"], reverse=True)
    lesions = lesions[: config.MAX_LESIONS]

    # 重新分配 id（排序后从 1 开始）
    for i, l in enumerate(lesions):
        l["id"] = i + 1
        l["label"] = f"病灶 {i + 1}"

    # ---- Summary ----
    lesion_count = len(lesions)
    max_diameter = round(max((l["diameterMm"] for l in lesions), default=0.0), 2)
    total_volume_mm3 = round(sum(l["volumeMm3"] for l in lesions), 2)
    total_volume_cm3 = round(total_volume_mm3 / 1000.0, 4)
    overall_risk = _overall_risk(lesions)

    summary = {
        "lesionCount": lesion_count,
        "maxDiameterMm": max_diameter,
        "totalVolumeMm3": total_volume_mm3,
        "totalVolumeCm3": total_volume_cm3,
        "overallRiskLevel": overall_risk,
        "modelVersion": model_version or config.ACTIVE_MODEL_VERSION,
        "processingTimeMs": processing_time_ms,
        "note": "AI 辅助结果仅供参考，非临床诊断依据，请结合临床综合判断。",
    }

    return lesions, summary
