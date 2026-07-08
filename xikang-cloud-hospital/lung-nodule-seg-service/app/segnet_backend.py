"""
SegNet 2D 推理后端
====================

移植自 Ola-Vish/lung-tumor-segmentation（MIT License，
https://github.com/Ola-Vish/lung-tumor-segmentation）。与 app/inference.py
（MONAI 3D 滑窗）、app/nnunet_backend.py（nnU-Net 整卷）不同，本后端按原仓库
约定逐轴位切片做 2D 推理：

  1. 读取 NRRD/NIfTI，重定向到 RAS 方向（轴位切片 = sitk 数组第 0 维，
     坐标约定与 ct-viewer-service 和 frontend 保持一致）
  2. 每张切片：原始 HU 值 / SEGNET_SCALING_VALUE（不做 clip，与原仓库训练
     预处理完全一致）→ resize 到 224x224 → 灰度复制为 3 通道（VGG 兼容输入）
  3. 按批喂入 SegNet，收集逐切片前景概率图（prob_map）
  4. 推理完成后，对 prob_map 在 Z 轴（切片轴）做 Gaussian 平滑：
     SegNet 是 2D 模型，每张切片独立预测，没有 3D 上下文约束，相邻切片间概率
     常常忽高忽低，导致 3D 掩码只有 1~2 层厚、在冠状/矢状图中呈扁平薄片状。
     Z 轴平滑将概率连续地传播到相邻切片，使 3D 病灶形态更自然。
  5. 用平滑后的概率图（> SEGNET_PROB_THRESHOLD）重算 binary_mask，
     沿 z 轴堆叠为完整体数据

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
    """
    重定向到 RAS 方向，与 ct-viewer-service 存储格式和 frontend 坐标约定保持一致：
      - D 轴（第 0 维）= Superior-Inferior（轴位切片索引）
      - H 轴（第 1 维）= Anterior-Posterior（冠状切片索引）
      - W 轴（第 2 维）= Right-Left（矢状切片索引）
    bbox / centroidXyz 均在此坐标系下计算，frontend navigateToLesion 依赖此约定。
    """
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

    输入切片在 RAS 空间下的内存排列为 (A-dim, R-dim)（行=前后, 列=左右），
    直接归一化后喂给模型，mask 输出与 hu_arr 保持完全相同的坐标约定，
    避免因转置引入的 H/W 轴混淆问题。

    返回 (3, H, W) float32 tensor（H, W 为目标 size）。
    """
    arr = slice_hu.astype(np.float32) / config.SEGNET_SCALING_VALUE
    tensor = torch.from_numpy(arr).unsqueeze(0).unsqueeze(0)  # (1, 1, H0, W0)
    tensor = F.interpolate(tensor, size=size, mode="bilinear", align_corners=False)
    tensor = tensor.repeat(1, 3, 1, 1)  # 灰度 -> 3 通道（VGG 兼容输入）
    return tensor.squeeze(0)  # (3, H, W)


def _smooth_prob_map_z(prob_map: np.ndarray, sigma: float) -> np.ndarray:
    """
    对 3D 概率图在 Z 轴（切片方向）做 Gaussian 平滑。

    SegNet 逐切片 2D 推理无 3D 上下文约束，相邻切片间预测概率常常不连续，
    导致 3D 掩码只有 1~2 层厚，在冠状/矢状图中呈扁平薄片状。
    对 Z 轴做 Gaussian 平滑（sigma 单位：切片数）将概率连续传播到邻近切片，
    使病灶在三维空间中更自然、更连贯。

    参数
    ----
    prob_map : (D, H, W) float32 概率图
    sigma    : Z 轴高斯标准差（单位：切片数），对应 ~sigma * slice_thickness mm
    """
    if sigma <= 0:
        return prob_map
    try:
        from scipy.ndimage import gaussian_filter
        # 仅在 Z 轴平滑，XY 平面保持原始分辨率
        return gaussian_filter(prob_map, sigma=(sigma, 0.0, 0.0))
    except ImportError:
        # 若 scipy 不可用，用 PyTorch avg_pool3d 做近似均匀平滑（备用）
        k = max(3, int(sigma * 2 + 1) | 1)  # 奇数 kernel
        pad = k // 2
        t = torch.from_numpy(prob_map).unsqueeze(0).unsqueeze(0)   # (1,1,D,H,W)
        t = F.avg_pool3d(t, kernel_size=(k, 1, 1), stride=1, padding=(pad, 0, 0))
        return t.squeeze().numpy()


def run_segnet_inference(model: torch.nn.Module, file_path: str) -> Dict[str, Any]:
    """
    对单个 NRRD/NIfTI 文件跑 SegNet 逐切片 2D 推理。

    返回契约与 app/nnunet_backend.py::run_nnunet_inference 一致，方便
    app/main.py 统一分发调用：
      binary_mask   : (D, H, W) uint8，0=背景 / 1=肿瘤（原始输入分辨率）
      prob_map      : (D, H, W) float32，前景概率（Z 轴平滑后）
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

            probs_resized = F.interpolate(
                probs.unsqueeze(1), size=(H, W), mode="bilinear", align_corners=False
            ).squeeze(1)

            probs_np = probs_resized.cpu().numpy()
            for offset, i in enumerate(range(start, end)):
                prob_map[i] = probs_np[offset]

    inference_ms = int(round((time.time() - t0) * 1000))

    # ---- Z 轴平滑：使 3D 掩码在冠状/矢状图中不呈扁平薄片 ----
    # 2D 逐切片模型在 Z 方向无约束，相邻切片预测独立且概率可能忽高忽低。
    # Gaussian 平滑把高概率区域传播到相邻切片，使同一病灶在 3D 中形态更连贯。
    smoothed_prob = _smooth_prob_map_z(prob_map, config.SEGNET_Z_SMOOTH_SIGMA)

    # 用平滑后的概率重算二值掩码，阈值略低于 0.5 以保留平滑后的传播区域
    binary_mask = (smoothed_prob > config.SEGNET_PROB_THRESHOLD).astype(np.uint8)

    return {
        "binary_mask": binary_mask,
        "prob_map": smoothed_prob,   # 返回平滑后的概率，供后处理的置信度过滤使用
        "hu_arr": hu_arr,
        "sitk_image": image,
        "inference_ms": inference_ms,
    }
