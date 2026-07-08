"""
滑窗推理 + 后处理 + 结果组装
============================

核心：sliding_window_inference
  - 把大 volume 切成 (32,224,224) 的 patch 网格
  - 步长 = patch // 2（50% 重叠）
  - 边角不足：zero-pad 到 patch 整数倍，推理后裁回原尺寸
  - 重叠区：3D 高斯权重加权累加 seg logits，最后除以权重和
  - 分类头 cls：所有 patch 的 cls logits 取平均

后处理：
  - seg logits → argmax → 每体素类别 (0..4)
  - 统计 1..4 类体素占比 = artifact_volume_ratio
  - severity：按 SEVERITY_THRESHOLDS 映射
  - has_artifact：sigmoid(cls_avg[0]) > CLS_THRESHOLD
  - artifact_types：sigmoid(cls_avg[1..4]) → dict(metal/beam_hardening/partial_volume/ring)
"""

from __future__ import annotations

import time
from typing import Dict, Any

import numpy as np
import torch
import torch.nn.functional as F

from . import config


# ========================
# 3D 高斯权重图
# ========================
def _gaussian_map(patch_size: tuple, sigma_scale: float = 0.125,
                  dtype=torch.float32, device="cpu") -> torch.Tensor:
    """
    生成一个 3D 高斯权重图，形状 = patch_size。
    中心为 1，边缘趋近 0，用于重叠区加权融合。

    参数
    ----
    patch_size : (D, H, W)
    sigma_scale : 标准差 = patch 维度 * sigma_scale（医学影像滑窗常用 1/8）

    返回
    ----
    torch.Tensor, shape=patch_size, 值域 (0, 1]
    """
    sigmas = [dim * sigma_scale for dim in patch_size]

    coords = []
    for dim, s in zip(patch_size, sigmas):
        # 以中心为 0 的一维坐标
        half = (dim - 1) / 2.0
        x = np.arange(dim, dtype=np.float32) - half
        coords.append(np.exp(-(x ** 2) / (2.0 * s * s)))

    # 外积 → 3D
    gauss = coords[0]
    for c in coords[1:]:
        gauss = np.outer(gauss, c).reshape(gauss.shape + (-1,))
    gauss = gauss.reshape(patch_size)

    # 防止全 0（极端情况下边缘权重过小）
    gauss = gauss / gauss.max()
    gauss[gauss < 1e-3] = 1e-3

    return torch.from_numpy(gauss).to(dtype=dtype, device=device)


# ========================
# 滑窗推理
# ========================
def sliding_window_inference(
    model: torch.nn.Module,
    volume: torch.Tensor,
    patch_size: tuple = config.PATCH_SIZE,
    overlap: float = config.OVERLAP,
    device: str = config.DEVICE,
    batch_size: int = 1,
):
    """
    对一个完整体数据做滑窗推理，返回整卷的 seg logits 和平均 cls logits。

    参数
    ----
    models : MultiTaskUNet3D（已 eval()、已加载权重）
    volume : torch.Tensor, shape=(1, 1, D, H, W)，已预处理
    patch_size : (pD, pH, pW)
    overlap : 0..1，重叠率；步长 = round(patch * (1-overlap))
              overlap=0.5 → 步长 = patch//2
    device : "cpu"
    batch_size : 一次前向塞几个 patch（CPU 内存小，默认 1）

    返回
    ----
    dict:
      seg_logits : (1, num_seg_classes, D, H, W)   —— 融合后的整卷 logits
      cls_logits : (1, num_cls)                    —— 所有 patch cls 的平均
    """
    model.eval()
    pD, pH, pW = patch_size
    # input shape: (1, 1, D, H, W)
    _, _, D_orig, H_orig, W_orig = volume.shape

    # ============================================================
    # 关键修复：先把 volume pad 到 patch_size 的整数倍
    # 否则当某维度长度 < patch_size 或不整除时，切片会切出比 patch 小的块，
    # 模型解码器的 skip-connection 就会因尺寸不匹配崩掉
    # （报错形如 "size of tensor a (113) must match b (224)"）。
    # pad 在末端补 0，推理后裁回原尺寸。
    # ============================================================
    def pad_to_multiple(n: int, p: int) -> int:
        return ((n + p - 1) // p) * p

    D_pad = pad_to_multiple(D_orig, pD)
    H_pad = pad_to_multiple(H_orig, pH)
    W_pad = pad_to_multiple(W_orig, pW)

    # F.pad 的顺序是反向的：最后一个维度在前 → (W_left, W_right, H_l, H_r, D_l, D_r, C_l, C_r, B_l, B_r)
    pad_t = (0, W_pad - W_orig,
             0, H_pad - H_orig,
             0, D_pad - D_orig,
             0, 0,
             0, 0)
    volume_padded = F.pad(volume, pad_t, mode="constant", value=0)

    # 之后所有操作都基于 padded 后的尺寸
    D, H, W = D_pad, H_pad, W_pad

    # ---- 步长：每个维度独立，overlap=0.5 → 步长 = patch//2 ----
    def make_starts(length: int, patch: int) -> list:
        step = max(1, int(round(patch * (1.0 - overlap))))
        if length <= patch:
            return [0]
        starts = list(range(0, length - patch + 1, step))
        # 确保最后一个 patch 覆盖到末端
        if starts[-1] != length - patch:
            starts.append(length - patch)
        # 去重保序
        seen = set()
        out = []
        for s in starts:
            if s not in seen:
                seen.add(s)
                out.append(s)
        return out

    d_starts = make_starts(D, pD)
    h_starts = make_starts(H, pH)
    w_starts = make_starts(W, pW)

    # ---- 累加容器（用 padded 尺寸，最后裁回原尺寸）----
    num_seg = config.NUM_SEG_CLASSES
    num_cls = config.NUM_CLS
    seg_acc = torch.zeros((1, num_seg, D, H, W),
                          dtype=torch.float32, device=device)
    weight_acc = torch.zeros((1, 1, D, H, W),
                             dtype=torch.float32, device=device)
    cls_sum = torch.zeros((1, num_cls), dtype=torch.float32, device=device)
    cls_count = 0

    # ---- 高斯权重（所有 patch 共用，因为 patch 尺寸固定）----
    gauss = _gaussian_map(patch_size, device=device)  # (pD, pH, pW)

    # ---- 收集所有 patch 的 (位置, 数据) ----
    # 注意：必须从 volume_padded 切片，否则按 padded 尺寸算的 starts 会切到原始 volume 之外，
    # 切出比 patch_size 小的块 → 模型解码器尺寸不匹配崩溃。
    patches = []
    for d0 in d_starts:
        for h0 in h_starts:
            for w0 in w_starts:
                patch = volume_padded[:, :, d0:d0 + pD, h0:h0 + pH, w0:w0 + pW]
                patches.append(((d0, h0, w0), patch))

    # ---- 分批前向 ----
    was_training = model.training
    model.eval()
    with torch.no_grad():
        for i in range(0, len(patches), batch_size):
            batch = patches[i:i + batch_size]
            positions = [p[0] for p in batch]
            tensors = torch.cat([p[1] for p in batch], dim=0).to(device)
            out = model(tensors)  # seg: (B, C, pD, pH, pW), cls: (B, num_cls)
            seg_out = out["seg"]
            cls_out = out["cls"]

            for b, pos in enumerate(positions):
                d0, h0, w0 = pos
                seg_b = seg_out[b:b + 1].to(device)            # (1, C, pD, pH, pW)
                cls_b = cls_out[b:b + 1].to(device)            # (1, num_cls)

                # 加权累加：seg_acc[region] += seg_b * gauss
                # weight_acc[region] += gauss
                g = gauss.view(1, 1, pD, pH, pW)               # (1,1,pD,pH,pW)
                seg_acc[:, :, d0:d0 + pD, h0:h0 + pH, w0:w0 + pW] += seg_b * g
                weight_acc[:, :, d0:d0 + pD, h0:h0 + pH, w0:w0 + pW] += g
                cls_sum += cls_b
                cls_count += 1

    if was_training:
        model.train()

    # ---- 融合：除以权重和（防 0 除）----
    weight_acc = torch.clamp(weight_acc, min=1e-6)
    seg_logits = seg_acc / weight_acc                          # (1, C, D_pad, H_pad, W_pad)

    # ---- 裁回原始尺寸（去掉 padding 部分）----
    seg_logits = seg_logits[:, :, :D_orig, :H_orig, :W_orig]

    cls_logits = cls_sum / max(cls_count, 1)                   # (1, num_cls)

    return {"seg_logits": seg_logits, "cls_logits": cls_logits}


# ========================
# 后处理 + 结果组装
# ========================
def _severity_from_ratio(ratio: float) -> str:
    """按 SEVERITY_THRESHOLDS 升序匹配。"""
    result = config.SEVERITY_THRESHOLDS[0][1]
    for threshold, name in config.SEVERITY_THRESHOLDS:
        if ratio >= threshold:
            result = name
    return result


def postprocess(seg_logits: torch.Tensor,
                cls_logits: torch.Tensor) -> Dict[str, Any]:
    """
    把模型原始输出转成 DESIGN §4.1 的 data 字段。

    参数
    ----
    seg_logits : (1, num_seg, D, H, W)
    cls_logits : (1, num_cls)

    返回
    ----
    dict:
      has_artifact : bool
      artifact_types : {metal, beam_hardening, partial_volume, ring} -> float
      artifact_volume_ratio : float
      severity : str
    （不含 inference_ms，由调用方拼上）
    """
    # ---- seg → argmax 得每体素类别 ----
    seg_pred = torch.argmax(seg_logits, dim=1).squeeze(0)      # (D, H, W), 值 0..4
    total_voxels = seg_pred.numel()

    # 类别 1..4 的体素占比
    artifact_voxels = (seg_pred > 0).sum().item()
    ratio = float(artifact_voxels) / float(max(total_voxels, 1))
    ratio = max(0.0, min(1.0, ratio))                          # 钳到 [0,1]

    # ---- cls → sigmoid ----
    cls_prob = torch.sigmoid(cls_logits).squeeze(0).cpu().numpy()  # (num_cls,)
    has_artifact = bool(cls_prob[0] > config.CLS_THRESHOLD)

    # cls[1..4] → artifact_types dict
    artifact_types = {}
    for i, key in enumerate(config.CLS_ARTIFACT_KEYS):
        # cls_prob[0] 是"有无伪影"，[1..4] 对应 4 种类型
        artifact_types[key] = float(cls_prob[i + 1])

    severity = _severity_from_ratio(ratio)

    return {
        "has_artifact": has_artifact,
        "artifact_types": artifact_types,
        "artifact_volume_ratio": round(ratio, 6),
        "severity": severity,
    }


def run_inference(model: torch.nn.Module,
                  volume: torch.Tensor) -> Dict[str, Any]:
    """
    端到端：滑窗推理 + 后处理 + 拼 inference_ms。

    参数
    ----
    models : 已加载权重的 MultiTaskUNet3D
    volume : (1, 1, D, H, W) 预处理后的张量

    返回
    ----
    dict（DESIGN §4.1 的 data 字段，含 inference_ms）
    """
    t0 = time.time()
    out = sliding_window_inference(model, volume)
    t1 = time.time()

    result = postprocess(out["seg_logits"], out["cls_logits"])
    result["inference_ms"] = int(round((t1 - t0) * 1000))
    return result
