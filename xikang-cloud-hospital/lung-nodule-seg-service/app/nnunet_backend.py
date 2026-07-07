"""
nnU-Net v2 推理后端
====================

对接官方 nnU-Net v2 框架（`nnunetv2` 包），作为 MONAI 自训练模型的可选替代后端。

使用方式：
  1. pip install -r requirements-nnunet.txt
  2. python scripts/download_nnunet_weights.py 下载并整理权重目录
  3. 设置环境变量 LUNG_NODULE_SEG_BACKEND=nnunet 后启动服务

与 app/inference.py（MONAI 滑窗推理）的关键区别：
  - nnU-Net 自己管理预处理（重采样 / 归一化），所以这里只需读取原图并转 RAS 方向，
    不能像 app/preprocess.py 那样先重采样到 config.SPACING_XYZ —— 重采样交给
    nnU-Net 内部按其 plans.json 完成。
  - predict_single_npy_array 返回的分割结果会被 nnU-Net 自动重采样回"输入图像"的
    原始分辨率，因此 mask 的 shape/spacing 与我们传入的 RAS 图完全一致，可以直接
    喂给 app/postprocess.py（它只依赖 sitk_image 的 spacing，不关心具体数值）。
  - 数组轴序与 spacing 顺序必须严格遵循 nnU-Net 的 SimpleITK 约定：
      npy_image : (C, Z, Y, X)  ==  sitk.GetArrayFromImage() 的 (Z,Y,X) 前面加一个通道维
      spacing   : (sz, sy, sx)  ==  sitk Image.GetSpacing()（x,y,z）反转
    这是照抄 nnunetv2.imageio.simpleitk_reader_writer.SimpleITKIO 的做法，
    详见 nnU-Net 官方 inference/readme.md。
"""

from __future__ import annotations

import os
import time
from typing import Any, Dict, Optional, Tuple

import numpy as np
import SimpleITK as sitk
import torch

from . import config


def _to_ras(image: sitk.Image) -> sitk.Image:
    return sitk.DICOMOrient(image, "RAS")


def load_predictor():
    """
    加载 nnU-Net 官方 nnUNetPredictor。

    返回
    ----
    (predictor, error_message)
    加载失败时 predictor 为 None，error_message 说明原因。
    """
    try:
        from nnunetv2.inference.predict_from_raw_data import nnUNetPredictor
    except ImportError as e:
        return None, (
            "未安装 nnunetv2，请先执行: "
            "pip install -r requirements-nnunet.txt "
            f"（原始错误: {e}）"
        )

    model_dir = os.path.join(
        config.NNUNET_RESULTS_DIR,
        config.NNUNET_DATASET_NAME,
        f"{config.NNUNET_TRAINER}__{config.NNUNET_PLANS}__{config.NNUNET_CONFIGURATION}",
    )
    checkpoint_path = os.path.join(
        model_dir, f"fold_{config.NNUNET_FOLD}", config.NNUNET_CHECKPOINT
    )
    if not os.path.isfile(checkpoint_path):
        return None, (
            f"nnU-Net checkpoint 不存在: {checkpoint_path}\n"
            "请先运行 python scripts/download_nnunet_weights.py 下载并整理权重目录，"
            "或检查 NNUNET_RESULTS_DIR / NNUNET_DATASET_NAME 等环境变量是否正确。"
        )

    try:
        device = torch.device(config.NNUNET_DEVICE)
        predictor = nnUNetPredictor(
            tile_step_size=config.NNUNET_STEP_SIZE,
            use_gaussian=True,
            use_mirroring=config.NNUNET_USE_MIRRORING,
            perform_everything_on_device=(device.type == "cuda"),
            device=device,
            verbose=False,
            verbose_preprocessing=False,
            allow_tqdm=False,
        )
        predictor.initialize_from_trained_model_folder(
            model_dir,
            use_folds=(config.NNUNET_FOLD,),
            checkpoint_name=config.NNUNET_CHECKPOINT,
        )
        return predictor, None
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def run_nnunet_inference(predictor, file_path: str) -> Dict[str, Any]:
    """
    对单个 NRRD/NIfTI 文件跑 nnU-Net 推理。

    返回
    ----
    dict:
      binary_mask   : (D, H, W) uint8，0=背景 / 1=肿瘤（原始输入分辨率）
      prob_map      : (D, H, W) float32，肿瘤概率图（原始输入分辨率）
      hu_arr        : (D, H, W) float32，原始 HU 值（未归一化，供密度计算）
      sitk_image    : RAS 方向、原始 spacing 的 sitk.Image，供后处理计算世界坐标
      inference_ms  : 推理耗时（毫秒）
    """
    image = sitk.ReadImage(file_path)
    image = _to_ras(image)

    hu_arr = sitk.GetArrayFromImage(image).astype(np.float32)  # (Z, Y, X)，原始 HU

    # 按 nnU-Net SimpleITKIO 约定构造输入：(C, Z, Y, X) + spacing (sz, sy, sx)
    npy_image = hu_arr[None].astype(np.float32)  # (1, Z, Y, X)
    spacing_zyx = list(image.GetSpacing())[::-1]  # (sx,sy,sz) -> (sz,sy,sx)
    props = {"spacing": [float(s) for s in spacing_zyx]}

    t0 = time.time()
    segmentation, probabilities = predictor.predict_single_npy_array(
        npy_image, props, None, None, True
    )
    inference_ms = int(round((time.time() - t0) * 1000))

    # segmentation: (Z, Y, X)，label id（0=背景，1=肿瘤）
    # probabilities: (num_classes, Z, Y, X)，softmax 概率
    binary_mask = (segmentation == 1).astype(np.uint8)
    if probabilities is not None and probabilities.shape[0] > 1:
        prob_map = np.asarray(probabilities[1], dtype=np.float32)
    else:
        # 极端情况下拿不到概率图，退化为用二值 mask 近似
        prob_map = binary_mask.astype(np.float32)

    return {
        "binary_mask": binary_mask,
        "prob_map": prob_map,
        "hu_arr": hu_arr,
        "sitk_image": image,
        "inference_ms": inference_ms,
    }
