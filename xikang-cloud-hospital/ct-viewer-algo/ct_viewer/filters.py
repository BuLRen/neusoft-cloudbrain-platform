"""CT 滤波算法（与 day2/dicom_viewer_gui.py 及桌面版逻辑一致）。"""

from __future__ import annotations

import SimpleITK as sitk

from .segmentation import LESION_DEMO_FILTER_NAME, generate_synthetic_lesion_mask

METAL_MASK_FILTER_NAME = "金属伪影掩码 Metal Artifact Mask"


def run_filter(image: sitk.Image, filter_name: str, params: dict) -> tuple[sitk.Image, bool]:
    """
    执行滤波，返回 (结果图像, 是否为掩码类型)。
    """
    image_float = sitk.Cast(image, sitk.sitkFloat32)

    if filter_name == "无滤波":
        return image_float, False

    if filter_name == "高斯滤波 Gaussian":
        sigma = float(params.get("spatial_sigma", 1.0))
        return sitk.SmoothingRecursiveGaussian(image_float, sigma), False

    if filter_name == "双边滤波 Bilateral":
        domain_sigma = float(params.get("spatial_sigma", 1.0))
        range_sigma = float(params.get("range_sigma", 50.0))
        bilateral = sitk.BilateralImageFilter()
        bilateral.SetDomainSigma(domain_sigma)
        bilateral.SetRangeSigma(range_sigma)
        return bilateral.Execute(image_float), False

    if filter_name == "中值滤波 Median":
        radius = int(params.get("median_radius", 1))
        median = sitk.MedianImageFilter()
        median.SetRadius([radius, radius, radius])
        return median.Execute(image_float), False

    if filter_name == "曲率流平滑 Curvature Flow":
        iterations = int(params.get("iterations", 5))
        time_step = float(params.get("time_step", 0.0625))
        result = sitk.CurvatureFlow(
            image_float, timeStep=time_step, numberOfIterations=iterations
        )
        return result, False

    if filter_name == "各向异性扩散 Anisotropic Diffusion":
        iterations = int(params.get("iterations", 5))
        time_step = float(params.get("time_step", 0.0625))
        conductance = float(params.get("conductance", 3.0))
        result = sitk.CurvatureAnisotropicDiffusion(
            image_float,
            timeStep=time_step,
            conductanceParameter=conductance,
            numberOfIterations=iterations,
        )
        return result, False

    if filter_name == METAL_MASK_FILTER_NAME:
        return _run_metal_mask_filter(image_float, params)

    if filter_name == LESION_DEMO_FILTER_NAME:
        source_name = str(params.get("source_name", ""))
        mask, _ = generate_synthetic_lesion_mask(image_float, source_name)
        return mask, True

    raise RuntimeError(f"未知滤波器：{filter_name}")


def _run_metal_mask_filter(image_float: sitk.Image, params: dict) -> tuple[sitk.Image, bool]:
    threshold_lower = float(params.get("metal_threshold_lower", 1000))
    threshold_upper = float(params.get("metal_threshold_upper", 4000))
    gradient_threshold = float(params.get("metal_gradient_threshold", 100))
    opening_radius = int(params.get("metal_opening_radius", 1))
    closing_radius = int(params.get("metal_closing_radius", 2))
    min_component_size = int(params.get("metal_min_component_size", 50))

    hu_mask = sitk.BinaryThreshold(
        image_float,
        lowerThreshold=threshold_lower,
        upperThreshold=threshold_upper,
        insideValue=1,
        outsideValue=0,
    )
    mask = sitk.Cast(hu_mask, sitk.sitkUInt8)

    gradient = sitk.GradientMagnitude(image_float)
    gradient_mask = sitk.BinaryThreshold(
        gradient,
        lowerThreshold=gradient_threshold,
        upperThreshold=1.0e12,
        insideValue=1,
        outsideValue=0,
    )
    gradient_mask = sitk.Cast(gradient_mask, sitk.sitkUInt8)

    if opening_radius > 0:
        opening = sitk.BinaryMorphologicalOpeningImageFilter()
        opening.SetKernelRadius([opening_radius] * mask.GetDimension())
        opening.SetForegroundValue(1)
        mask = opening.Execute(mask)

    if closing_radius > 0:
        closing = sitk.BinaryMorphologicalClosingImageFilter()
        closing.SetKernelRadius([closing_radius] * mask.GetDimension())
        closing.SetForegroundValue(1)
        mask = closing.Execute(mask)

    connected = sitk.ConnectedComponent(mask)
    if min_component_size > 0:
        relabeled = sitk.RelabelComponent(connected, minimumObjectSize=min_component_size)
    else:
        relabeled = connected

    edge_labels = sitk.Mask(relabeled, gradient_mask)
    label_stats = sitk.LabelShapeStatisticsImageFilter()
    label_stats.Execute(edge_labels)

    filtered_mask = sitk.Image(mask.GetSize(), sitk.sitkUInt8)
    filtered_mask.CopyInformation(mask)
    for label in label_stats.GetLabels():
        component_mask = sitk.BinaryThreshold(
            relabeled,
            lowerThreshold=label,
            upperThreshold=label,
            insideValue=1,
            outsideValue=0,
        )
        filtered_mask = sitk.Or(
            filtered_mask, sitk.Cast(component_mask, sitk.sitkUInt8)
        )

    result = sitk.Cast(filtered_mask * 255, sitk.sitkUInt8)
    return result, True
