"""
SegNet 2D 推理后端
====================

移植自 Ola-Vish/lung-tumor-segmentation（MIT License，
https://github.com/Ola-Vish/lung-tumor-segmentation）。与 app/inference.py
（MONAI 3D 滑窗）、app/nnunet_backend.py（nnU-Net 整卷）不同，本后端按原仓库
约定逐轴位切片做 2D 推理：

  1. 读取 NRRD/NIfTI，重定向到 RAS 方向（轴位切片 = sitk 数组第 0 维）
  2. 每张切片：原始 HU 值 / SEGNET_SCALING_VALUE（不做 clip，与原仓库训练
     预处理完全一致）→ resize 到 224x224 → 灰度复制为 3 通道（VGG 兼容输入）
  3. 按批喂入 SegNet，取前景类 softmax 概率与 argmax 分割结果
  4. 概率图 / 分割结果各自 resize 回原始切片尺寸，沿 z 轴堆叠为完整体数据

使用方式：
  1. 从 https://github.com/Ola-Vish/lung-tumor-segmentation 的 README 中
     「My SegNet checkpoint can be downloaded from this link」下载作者提供
     的 checkpoint
  2. python -m scripts.convert_segnet_checkpoint --src <下载的 .ckpt>
  3. 重启服务，GET /health 的 available_models 中 segnet.loaded 应变为 true
"""

from __future__ import annotations

import os
import time
from typing import Any, Dict, Optional, Tuple

import numpy as np
import SimpleITK as sitk
import torch
import torch.nn.functional as F

from . import config
from .segnet_checkpoint_utils import load_segnet_state_dict
from .segnet_model import build_segnet_model


def _to_ras(image: sitk.Image) -> sitk.Image:
    return sitk.DICOMOrient(image, "RAS")


def load_model() -> Tuple[Optional[torch.nn.Module], Optional[str]]:
    """
    加载 SegNet 模型。

    返回
    ----
    (model, error_message)：加载失败时 model 为 None，error_message 说明原因。
    """
    path = config.SEGNET_MODEL_PATH
    if not os.path.isfile(path):
        return None, (
            f"SegNet 权重文件不存在: {path}\n"
            "请先从 https://github.com/Ola-Vish/lung-tumor-segmentation 的 README 下载 "
            "checkpoint，再执行 python -m scripts.convert_segnet_checkpoint 转换后放到该路径"
            "（详见本服务 README「SegNet 后端」一节）。"
        )
    try:
        state_dict = load_segnet_state_dict(path)
        model = build_segnet_model(warm_start=False)
        missing, unexpected = model.load_state_dict(state_dict, strict=False)
        if missing:
            return None, (
                f"SegNet 权重缺少 {len(missing)} 个 key（可能架构不匹配或文件损坏）: "
                f"{missing[:5]}..."
            )
        device = torch.device(config.SEGNET_DEVICE)
        model.to(device)
        model.eval()
        return model, None
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def _preprocess_slice(slice_hu: np.ndarray, size: Tuple[int, int]) -> torch.Tensor:
    """
    单张轴位切片预处理：HU / scaling_value（不 clip）→ resize → 灰度转 3 通道。

    返回 (3, H, W) float32 tensor（H, W 为目标 size）。
    """
    arr = slice_hu.astype(np.float32) / config.SEGNET_SCALING_VALUE
    tensor = torch.from_numpy(arr).unsqueeze(0).unsqueeze(0)  # (1, 1, H0, W0)
    tensor = F.interpolate(tensor, size=size, mode="bilinear", align_corners=False)
    tensor = tensor.repeat(1, 3, 1, 1)  # 灰度 -> 3 通道（VGG 兼容输入）
    return tensor.squeeze(0)  # (3, H, W)


def run_segnet_inference(model: torch.nn.Module, file_path: str) -> Dict[str, Any]:
    """
    对单个 NRRD/NIfTI 文件跑 SegNet 逐切片 2D 推理。

    返回契约与 app/nnunet_backend.py::run_nnunet_inference 一致，方便
    app/main.py 统一分发调用：
      binary_mask   : (D, H, W) uint8，0=背景 / 1=肿瘤（原始输入分辨率）
      prob_map      : (D, H, W) float32，前景概率
      hu_arr        : (D, H, W) float32，原始 HU 值（未归一化，供密度计算）
      sitk_image    : RAS 方向、原始 spacing 的 sitk.Image
      inference_ms  : 推理耗时（毫秒）
    """
    image = sitk.ReadImage(file_path)
    image = _to_ras(image)
    hu_arr = sitk.GetArrayFromImage(image).astype(np.float32, copy=False)  # (D, H, W)

    device = torch.device(config.SEGNET_DEVICE)
    D, H, W = hu_arr.shape
    size = config.SEGNET_INPUT_SIZE

    binary_mask = np.zeros((D, H, W), dtype=np.uint8)
    prob_map = np.zeros((D, H, W), dtype=np.float32)

    t0 = time.time()
    batch_size = max(1, config.SEGNET_BATCH_SIZE)
    with torch.no_grad():
        for start in range(0, D, batch_size):
            end = min(start + batch_size, D)
            batch_slices = [_preprocess_slice(hu_arr[i], size) for i in range(start, end)]
            batch_tensor = torch.stack(batch_slices, dim=0).to(device)  # (B, 3, h, w)

            logits = model(batch_tensor)  # (B, 2, h, w)
            probs = torch.softmax(logits, dim=1)[:, 1]  # 前景概率 (B, h, w)
            preds = torch.argmax(logits, dim=1)  # (B, h, w)

            probs_resized = F.interpolate(
                probs.unsqueeze(1), size=(H, W), mode="bilinear", align_corners=False
            ).squeeze(1)
            preds_resized = F.interpolate(
                preds.unsqueeze(1).to(torch.float32), size=(H, W), mode="nearest"
            ).squeeze(1)

            probs_np = probs_resized.cpu().numpy()
            preds_np = preds_resized.cpu().numpy()
            for offset, i in enumerate(range(start, end)):
                prob_map[i] = probs_np[offset]
                binary_mask[i] = (preds_np[offset] >= 0.5).astype(np.uint8)

    inference_ms = int(round((time.time() - t0) * 1000))

    return {
        "binary_mask": binary_mask,
        "prob_map": prob_map,
        "hu_arr": hu_arr,
        "sitk_image": image,
        "inference_ms": inference_ms,
    }
