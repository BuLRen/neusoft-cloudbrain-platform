"""CT 病灶分割演示（规则检测 + 确定性合成回退，仅供教学演示）。"""

from __future__ import annotations

import hashlib
from typing import Any

import numpy as np
import SimpleITK as sitk

LESION_DEMO_FILTER_NAME = "病灶分割演示 Lesion Segmentation (Demo)"
DEMO_DISCLAIMER = "仅用于教学演示，非临床诊断依据"


def _seed_from_source(source_name: str, image: sitk.Image) -> int:
    size = image.GetSize()
    key = f"{source_name}|{size[0]}|{size[1]}|{size[2]}"
    return int(hashlib.md5(key.encode("utf-8")).hexdigest()[:8], 16)


def generate_synthetic_lesion_mask(
    image: sitk.Image,
    source_name: str = "",
    lesion_count: int | None = None,
) -> tuple[sitk.Image, list[dict[str, Any]]]:
    """用确定性随机种子在体数据中心区域生成 1-3 个椭球形演示病灶。"""
    rng = np.random.RandomState(_seed_from_source(source_name, image))
    size = image.GetSize()
    spacing = image.GetSpacing()

    mask_arr = np.zeros((size[2], size[1], size[0]), dtype=np.uint8)
    cx, cy, cz = size[0] // 2, size[1] // 2, size[2] // 2

    if lesion_count is None:
        lesion_count = 1 + int(rng.randint(0, 3))

    lesions: list[dict[str, Any]] = []
    for i in range(lesion_count):
        ox = int(cx + rng.randint(-max(size[0] // 6, 1), max(size[0] // 6, 1) + 1))
        oy = int(cy + rng.randint(-max(size[1] // 6, 1), max(size[1] // 6, 1) + 1))
        oz = int(cz + rng.randint(-max(size[2] // 6, 1), max(size[2] // 6, 1) + 1))

        radius_mm = 4.0 + float(rng.random() * 10.0)
        rx = max(2, int(radius_mm / max(spacing[0], 0.1)))
        ry = max(2, int(radius_mm / max(spacing[1], 0.1)))
        rz = max(1, int(radius_mm / max(spacing[2], 0.1)))

        x0, x1 = max(0, ox - rx), min(size[0], ox + rx + 1)
        y0, y1 = max(0, oy - ry), min(size[1], oy + ry + 1)
        z0, z1 = max(0, oz - rz), min(size[2], oz + rz + 1)

        for z in range(z0, z1):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    dx = (x - ox) / max(rx, 1)
                    dy = (y - oy) / max(ry, 1)
                    dz = (z - oz) / max(rz, 1)
                    if dx * dx + dy * dy + dz * dz <= 1.0:
                        mask_arr[z, y, x] = 255

        diameter_mm = 2.0 * radius_mm
        lesions.append(
            {
                "id": i + 1,
                "label": "疑似结节" if i % 2 == 0 else "异常密度影",
                "sliceIndex": oz,
                "plane": "axial",
                "centroidXyz": [ox, oy, oz],
                "diameterMm": round(diameter_mm, 1),
                "bbox": [x0, y0, z0, x1 - 1, y1 - 1, z1 - 1],
                "confidence": round(0.65 + float(rng.random() * 0.25), 2),
                "volumeMm3": round((4.0 / 3.0) * np.pi * radius_mm**3, 1),
                "source": "synthetic",
            }
        )

    mask_image = sitk.GetImageFromArray(mask_arr)
    mask_image.CopyInformation(image)
    return sitk.Cast(mask_image, sitk.sitkUInt8), lesions


def _build_summary(lesions: list[dict[str, Any]], method: str) -> dict[str, Any]:
    return {
        "lesionCount": len(lesions),
        "maxDiameterMm": max((float(l["diameterMm"]) for l in lesions), default=0.0),
        "method": method,
        "note": DEMO_DISCLAIMER,
    }


def run_rule_based_segmentation(
    image: sitk.Image,
    source_name: str = "",
    params: dict | None = None,
) -> tuple[sitk.Image, list[dict[str, Any]], dict[str, Any]]:
    """
    规则型结节样检测：肺窗 HU 阈值 + 形态学 + 连通域筛选。
    未检出时回退到确定性合成病灶，保证演示总有结果。
    """
    params = params or {}
    image_float = sitk.Cast(image, sitk.sitkFloat32)

    hu_lower = float(params.get("nodule_hu_lower", -600))
    hu_upper = float(params.get("nodule_hu_upper", 100))
    min_size = int(params.get("min_component_voxels", 20))
    max_size = int(params.get("max_component_voxels", 50000))

    candidate = sitk.BinaryThreshold(image_float, hu_lower, hu_upper, 1, 0)
    candidate = sitk.Cast(candidate, sitk.sitkUInt8)

    opening = sitk.BinaryMorphologicalOpeningImageFilter()
    opening.SetKernelRadius([1, 1, 1])
    opening.SetForegroundValue(1)
    candidate = opening.Execute(candidate)

    closing = sitk.BinaryMorphologicalClosingImageFilter()
    closing.SetKernelRadius([1, 1, 1])
    closing.SetForegroundValue(1)
    candidate = closing.Execute(candidate)

    connected = sitk.ConnectedComponent(candidate)
    relabeled = sitk.RelabelComponent(connected, minimumObjectSize=min_size)

    stats = sitk.LabelShapeStatisticsImageFilter()
    stats.Execute(relabeled)

    spacing = image.GetSpacing()
    size = image.GetSize()
    output_mask = sitk.Image(size, sitk.sitkUInt8)
    output_mask.CopyInformation(image)

    lesions: list[dict[str, Any]] = []
    lesion_id = 0
    for label in stats.GetLabels():
        if label == 0:
            continue
        num_pixels = stats.GetNumberOfPixels(label)
        if num_pixels > max_size:
            continue

        physical_size = stats.GetPhysicalSize(label)
        equiv_diameter_mm = 2.0 * ((3.0 * physical_size / (4.0 * np.pi)) ** (1.0 / 3.0))
        if equiv_diameter_mm < 3.0 or equiv_diameter_mm > 30.0:
            continue

        lesion_id += 1
        component = sitk.BinaryThreshold(relabeled, label, label, 255, 0)
        component = sitk.Cast(component, sitk.sitkUInt8)
        output_mask = sitk.Or(output_mask, component)

        bbox = stats.GetBoundingBox(label)
        centroid_physical = stats.GetCentroid(label)
        centroid_xyz = [
            int(round(centroid_physical[0] / spacing[0])) if spacing[0] else 0,
            int(round(centroid_physical[1] / spacing[1])) if spacing[1] else 0,
            int(round(centroid_physical[2] / spacing[2])) if spacing[2] else 0,
        ]
        centroid_xyz = [
            max(0, min(size[0] - 1, centroid_xyz[0])),
            max(0, min(size[1] - 1, centroid_xyz[1])),
            max(0, min(size[2] - 1, centroid_xyz[2])),
        ]

        lesions.append(
            {
                "id": lesion_id,
                "label": "疑似结节",
                "sliceIndex": centroid_xyz[2],
                "plane": "axial",
                "centroidXyz": centroid_xyz,
                "diameterMm": round(equiv_diameter_mm, 1),
                "bbox": [
                    bbox[0],
                    bbox[1],
                    bbox[2],
                    bbox[0] + bbox[3] - 1,
                    bbox[1] + bbox[4] - 1,
                    bbox[2] + bbox[5] - 1,
                ],
                "confidence": round(min(0.95, 0.5 + num_pixels / 500.0), 2),
                "volumeMm3": round(physical_size, 1),
                "source": "rule_based",
            }
        )
        if lesion_id >= 5:
            break

    if lesions:
        return output_mask, lesions, _build_summary(lesions, "rule_based")

    output_mask, lesions = generate_synthetic_lesion_mask(image, source_name)
    return output_mask, lesions, _build_summary(lesions, "synthetic_fallback")
