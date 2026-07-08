"""
滑窗推理
========

对完整 3D 体数据做滑窗推理，返回二值分割概率图。

与 ai-ct-service/inference.py 类似，使用高斯权重加权融合重叠区域。
区别：输出为二值概率图（通道 1 = 肿瘤的预测概率），而非多类别。
"""

from __future__ import annotations

import time
from typing import Dict, Any

import numpy as np
import torch
import torch.nn.functional as F

from . import config


def _gaussian_map(
    patch_size: tuple,
    sigma_scale: float = 0.125,
    dtype=torch.float32,
    device: str = "cpu",
) -> torch.Tensor:
    sigmas = [dim * sigma_scale for dim in patch_size]
    coords = []
    for dim, s in zip(patch_size, sigmas):
        half = (dim - 1) / 2.0
        x = np.arange(dim, dtype=np.float32) - half
        coords.append(np.exp(-(x ** 2) / (2.0 * s * s)))

    gauss = coords[0]
    for c in coords[1:]:
        gauss = np.outer(gauss, c).reshape(gauss.shape + (-1,))
    gauss = gauss.reshape(patch_size)
    gauss = gauss / gauss.max()
    gauss[gauss < 1e-3] = 1e-3
    return torch.from_numpy(gauss).to(dtype=dtype, device=device)


def sliding_window_inference(
    model: torch.nn.Module,
    volume: torch.Tensor,
    patch_size: tuple = config.PATCH_SIZE,
    overlap: float = config.OVERLAP,
    device: str = config.DEVICE,
    batch_size: int = 1,
) -> torch.Tensor:
    """
    滑窗推理，返回整卷的前景（肿瘤）预测概率图。

    参数
    ----
    model : 已加载权重且 eval() 的模型
    volume : (1, 1, D, H, W) float32 预处理后的张量

    返回
    ----
    prob_map : (D, H, W) float32 tensor，值域 [0, 1]，表示每体素为肿瘤的概率
    """
    model.eval()
    pD, pH, pW = patch_size
    _, _, D_orig, H_orig, W_orig = volume.shape

    def pad_to_multiple(n: int, p: int) -> int:
        return ((n + p - 1) // p) * p

    D_pad = pad_to_multiple(D_orig, pD)
    H_pad = pad_to_multiple(H_orig, pH)
    W_pad = pad_to_multiple(W_orig, pW)

    pad_t = (
        0, W_pad - W_orig,
        0, H_pad - H_orig,
        0, D_pad - D_orig,
        0, 0,
        0, 0,
    )
    volume_padded = F.pad(volume, pad_t, mode="constant", value=0.0)
    D, H, W = D_pad, H_pad, W_pad

    def make_starts(length: int, patch: int) -> list:
        step = max(1, int(round(patch * (1.0 - overlap))))
        if length <= patch:
            return [0]
        starts = list(range(0, length - patch + 1, step))
        if starts[-1] != length - patch:
            starts.append(length - patch)
        seen: set = set()
        out = []
        for s in starts:
            if s not in seen:
                seen.add(s)
                out.append(s)
        return out

    d_starts = make_starts(D, pD)
    h_starts = make_starts(H, pH)
    w_starts = make_starts(W, pW)

    # 累加容器（概率图，通道 1 = 肿瘤）
    prob_acc = torch.zeros((D, H, W), dtype=torch.float32, device=device)
    weight_acc = torch.zeros((D, H, W), dtype=torch.float32, device=device)

    gauss = _gaussian_map(patch_size, device=device)  # (pD, pH, pW)

    patches = []
    for d0 in d_starts:
        for h0 in h_starts:
            for w0 in w_starts:
                patch = volume_padded[:, :, d0:d0 + pD, h0:h0 + pH, w0:w0 + pW]
                patches.append(((d0, h0, w0), patch))

    with torch.no_grad():
        for i in range(0, len(patches), batch_size):
            batch = patches[i:i + batch_size]
            positions = [p[0] for p in batch]
            tensors = torch.cat([p[1] for p in batch], dim=0).to(device)
            logits = model(tensors)   # (B, 2, pD, pH, pW)
            probs = torch.softmax(logits, dim=1)[:, 1]  # 取通道 1（肿瘤概率），(B, pD, pH, pW)

            for b, (d0, h0, w0) in enumerate(positions):
                p_b = probs[b]  # (pD, pH, pW)
                g = gauss
                prob_acc[d0:d0 + pD, h0:h0 + pH, w0:w0 + pW] += p_b * g
                weight_acc[d0:d0 + pD, h0:h0 + pH, w0:w0 + pW] += g

    weight_acc = torch.clamp(weight_acc, min=1e-6)
    prob_map = prob_acc / weight_acc

    # 裁回原始尺寸
    prob_map = prob_map[:D_orig, :H_orig, :W_orig]
    return prob_map  # (D, H, W)


def run_inference(
    model: torch.nn.Module,
    volume: torch.Tensor,
) -> Dict[str, Any]:
    """
    端到端推理：滑窗 + 二值化，返回 {prob_map, binary_mask, inference_ms}。

    参数
    ----
    model  : 已加载权重的 UNet
    volume : (1, 1, D, H, W) 预处理后的张量

    返回
    ----
    dict:
      prob_map    : (D, H, W) numpy float32，肿瘤概率图
      binary_mask : (D, H, W) numpy uint8，0=背景 / 1=肿瘤
      inference_ms: int，推理耗时（毫秒）
    """
    t0 = time.time()
    prob_map = sliding_window_inference(model, volume)
    inference_ms = int(round((time.time() - t0) * 1000))

    prob_np = prob_map.cpu().numpy()
    binary_mask = (prob_np >= config.SEG_THRESHOLD).astype(np.uint8)

    return {
        "prob_map": prob_np,
        "binary_mask": binary_mask,
        "inference_ms": inference_ms,
    }
