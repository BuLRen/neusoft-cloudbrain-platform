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


def _to_lps(image: sitk.Image) -> sitk.Image:
    """
    重定向到 LPS 方向，与 ct-viewer-algo 存储 NRRD 的方向保持一致。

    ct-viewer-algo 的 write_image_to_nrrd 直接调用 sitk.WriteImage，
    不做任何方向转换，因此 CT NRRD 以 DICOM 原始方向存储。
    临床 CT 的 DICOM 标准方向是 LPS（Left-Posterior-Superior）：
      - x 轴（NRRD 第 1 维，最快变化）= Left 方向；x=0 对应病人右侧
      - y 轴（NRRD 第 2 维）= Posterior 方向
      - z 轴（NRRD 第 3 维，最慢变化）= Superior 方向（轴位切片索引）

    frontend 用 extractSliceZyx/extractCoronalSlice/extractSagittalSlice 读取
    NRRD 时，约定 x=0 在屏幕左侧。对于 LPS，x=0 = 病人右侧，与放射学惯例
    （"R 在屏幕左"）一致。

    如果 mask NRRD 用 RAS 方向（x=Right），则 x=0 对应病人左侧，显示时
    左右轴被镜像——这就是之前标注位置总是左右翻转的根本原因。

    本函数确保 mask NRRD 与 CT NRRD 使用相同的 LPS 方向，避免镜像问题。

    GetArrayFromImage(LPS_image) 返回 arr[z, y, x] = arr[S, P, L]，
    轴位切片 arr[i] shape = (P_dim, L_dim) = (H, W)：
      - D 轴 (axis 0) = S = 轴位切片索引
      - H 轴 (axis 1) = P = 冠状切片索引（P 方向，Y 维度）
      - W 轴 (axis 2) = L = 矢状切片索引（L 方向，X 维度）
    bbox / centroidXyz 均在此坐标系下计算，frontend navigateToLesion 依赖此约定。
    """
    return sitk.DICOMOrient(image, "LPS")


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
    单张轴位切片预处理：坐标系对齐 → HU / scaling_value（不 clip）→ resize → 灰度转 3 通道。

    坐标系对齐（关键）
    ------------------
    训练数据使用 nibabel LAS 方向读取，切片 data[:, :, idx] 的内存排列是
    (L_dim, A_dim)——行=Left（左右），列=Anterior（前后）。

    我们用 SimpleITK LPS 方向，GetArrayFromImage 返回 (D, H, W) = (S, P, L)，
    单张切片 hu_arr[i] 的内存排列是 (P_dim, L_dim)——行=Posterior，列=Left。

    变换 (P, L) → (L, A)：
      1. .T        : (P, L) → (L, P)，行=L，列=P
      2. np.fliplr : 把列反转 P→A（A 与 P 互为镜像），得 (L, A)，行=L，列=A ✓

    变换后 shape 从 (H=P_dim, W=L_dim) 变为 (W=L_dim, H=P_dim)。

    输出概率图需用逆变换 np.fliplr(probs).T 映射回 (P, L) LPS 空间
    （见 run_segnet_inference 推理循环）。

    返回 (3, W, H) float32 tensor（W=L_dim, H=P_dim，即对齐后的 (L, A) 维度）。
    """
    # (P, L) → (L, A)：转置后沿列方向翻转，使行=L、列=A，与训练数据内存排列一致
    arr = np.fliplr(slice_hu.T).astype(np.float32) / config.SEGNET_SCALING_VALUE
    tensor = torch.from_numpy(arr).unsqueeze(0).unsqueeze(0)  # (1, 1, W, H)
    tensor = F.interpolate(tensor, size=size, mode="bilinear", align_corners=False)
    tensor = tensor.repeat(1, 3, 1, 1)  # 灰度 -> 3 通道（VGG 兼容输入）
    return tensor.squeeze(0)  # (3, 224, 224)


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
      sitk_image    : LPS 方向、原始 spacing 的 sitk.Image（与 CT NRRD 方向一致）
      inference_ms  : 推理耗时（毫秒）
    """
    image = sitk.ReadImage(file_path)
    image = _to_lps(image)
    hu_arr = sitk.GetArrayFromImage(image).astype(np.float32, copy=False)  # (D, H, W)

    device = torch.device(config.SEGNET_DEVICE)
    D, H, W = hu_arr.shape
    size = config.SEGNET_INPUT_SIZE

    prob_map = np.zeros((D, H, W), dtype=np.float32)

    # _preprocess_slice 内部对切片做了 (P,L)→(L,A) 的坐标变换（np.fliplr(.T)），
    # 变换后切片 shape 从 (H=P_dim, W=L_dim) 变为 (W=L_dim, H=P_dim)。
    # 模型输出也在 (L,A) 坐标系下，因此 resize 回原始尺寸时应使用 (W, H)，而非 (H, W)。
    # 之后对每张概率图执行逆变换 np.fliplr(probs).T：(L,A)→(P,L)，
    # 使 prob_map[i] 的坐标约定与 hu_arr[i] 保持一致（均为 LPS 的 (P, L) 排列）。
    t0 = time.time()
    batch_size = max(1, config.SEGNET_BATCH_SIZE)
    with torch.no_grad():
        for start in range(0, D, batch_size):
            end = min(start + batch_size, D)
            batch_slices = [_preprocess_slice(hu_arr[i], size) for i in range(start, end)]
            batch_tensor = torch.stack(batch_slices, dim=0).to(device)  # (B, 3, 224, 224)

            logits = model(batch_tensor)  # (B, 2, 224, 224)
            probs = torch.softmax(logits, dim=1)[:, 1]  # 前景概率 (B, 224, 224)

            # 输出 resize 回 (W, H) 而非 (H, W)：
            # 模型输入经 np.fliplr(.T) 后 shape 为 (W=L_dim, H=P_dim)，输出在同一 (L,A) 空间，
            # 所以 resize 目标应与预处理输入的空间维度匹配，即 (W, H)。
            probs_resized = F.interpolate(
                probs.unsqueeze(1), size=(W, H), mode="bilinear", align_corners=False
            ).squeeze(1)  # (B, W, H) 在 (L, A) 坐标系下

            probs_np = probs_resized.cpu().numpy()
            for offset, i in enumerate(range(start, end)):
                # 逆变换 (L, A) → (P, L)：先左右翻转再转置
                # np.fliplr(probs)[l, H-1-a] = probs[l, a]
                # .T[H-1-a, l] = probs[l, a] → result[p, l] = probs[l, A-1-p]
                # 其中 p = H-1-a（P-index = H-1-A-index，P 与 A 互为镜像）✓
                prob_map[i] = np.fliplr(probs_np[offset]).T  # → (H, W) in (P, L) LPS

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
