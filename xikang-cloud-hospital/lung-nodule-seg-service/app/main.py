"""
lung-nodule-seg-service — 肺结节 AI 分割推理服务（FastAPI）

支持两种可切换的推理后端（LUNG_NODULE_SEG_BACKEND 环境变量控制）：
  - "monai"  ：本仓库 training/ 自训练的轻量 3D UNet（默认）
  - "nnunet" ：官方 nnU-Net v2 框架权重（更大网络，参见 app/nnunet_backend.py）

路由：
  GET  /health               健康检查（裸 JSON，不走统一格式）
  POST /internal/segment     肺结节分割（JSON body，与 ct-viewer-algo 相同风格）

API 设计（与 ct-viewer-algo 保持一致）：
  请求 body: {src_nrrd_path, out_nrrd_path, source_name}
  服务读取 src_nrrd_path 处的 NRRD，运行推理，把掩码写到 out_nrrd_path，
  返回 {is_mask, meta, lesions, summary, message} 的 JSON。

并发保护：asyncio.Lock（同时只跑一个推理）。
"""

from __future__ import annotations

import asyncio
import os
import time
import traceback
from contextlib import asynccontextmanager
from typing import Any, Dict, Optional

import numpy as np
import SimpleITK as sitk
import torch
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import config
from .model import build_model
from .preprocess import load_and_preprocess, get_original_hu_array
from .inference import run_inference
from .postprocess import extract_lesions


# ========================
# 全局状态
# ========================
_state: dict = {
    "backend": config.ACTIVE_BACKEND,
    "model": None,           # monai 后端：torch.nn.Module；nnunet 后端：nnUNetPredictor
    "model_loaded": False,
    "device": config.DEVICE if config.ACTIVE_BACKEND == "monai" else config.NNUNET_DEVICE,
    "model_version": config.ACTIVE_MODEL_VERSION,
    "load_error": None,
}

_inference_lock = asyncio.Lock()


# ========================
# 统一响应 helper
# ========================
def _ok(data: dict) -> dict:
    return {"code": 200, "message": "success", "data": data}


def _err(http_status: int, code: int, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=http_status,
        content={"code": code, "message": message, "data": None},
    )


# ========================
# 模型加载
# ========================
def _load_model() -> Optional[torch.nn.Module]:
    if not os.path.isfile(config.MODEL_PATH):
        _state["load_error"] = f"权重文件不存在: {config.MODEL_PATH}"
        return None
    try:
        model = build_model()
        sd = torch.load(
            config.MODEL_PATH,
            map_location=config.DEVICE,
            weights_only=False,
        )
        # 兼容多种保存格式
        if isinstance(sd, dict) and "model" in sd:
            raw_sd = sd["model"]
        elif isinstance(sd, dict) and "state_dict" in sd:
            raw_sd = sd["state_dict"]
        else:
            raw_sd = sd

        clean_sd = {
            (k[len("module."):] if k.startswith("module.") else k): v
            for k, v in raw_sd.items()
        }
        model.load_state_dict(clean_sd)
        model.to(config.DEVICE)
        model.eval()
        _state["load_error"] = None
        return model
    except Exception as e:
        _state["load_error"] = f"{type(e).__name__}: {e}"
        return None


@asynccontextmanager
async def lifespan(app: FastAPI):
    if config.ACTIVE_BACKEND == "nnunet":
        from .nnunet_backend import load_predictor

        print(f"[lung-nodule-seg-service] 使用 nnU-Net 后端，正在加载权重...")
        predictor, err = load_predictor()
        if predictor is not None:
            _state["model"] = predictor
            _state["model_loaded"] = True
            _state["load_error"] = None
            print(f"[lung-nodule-seg-service] nnU-Net 模型加载成功: {config.NNUNET_MODEL_VERSION}")
        else:
            _state["model"] = None
            _state["model_loaded"] = False
            _state["load_error"] = err
            print(f"[lung-nodule-seg-service] nnU-Net 模型未加载（服务仍可启动）: {err}")
    else:
        model = _load_model()
        if model is not None:
            _state["model"] = model
            _state["model_loaded"] = True
            print(f"[lung-nodule-seg-service] 模型加载成功: {config.MODEL_PATH}")
        else:
            _state["model"] = None
            _state["model_loaded"] = False
            print(f"[lung-nodule-seg-service] 模型未加载（服务仍可启动）: {_state['load_error']}")
    yield
    _state["model"] = None
    _state["model_loaded"] = False


# ========================
# FastAPI app
# ========================
app = FastAPI(
    title="lung-nodule-seg-service",
    description=(
        f"肺结节 AI 分割推理服务（当前后端: {config.ACTIVE_BACKEND}，"
        f"模型版本: {config.ACTIVE_MODEL_VERSION}，基于 Task06_Lung 训练）"
    ),
    version="1.0.0",
    lifespan=lifespan,
)


# ========================
# 路由
# ========================
@app.get("/health")
async def health():
    return {
        "ok": _state["model_loaded"],
        "model_loaded": _state["model_loaded"],
        "backend": _state["backend"],
        "device": _state["device"],
        "model_version": _state["model_version"],
        "load_error": _state["load_error"],
    }


class SegmentRequest(BaseModel):
    src_nrrd_path: str
    out_nrrd_path: str
    source_name: str = ""
    params: Dict[str, Any] = Field(default_factory=dict)


@app.post("/internal/segment")
async def internal_segment(body: SegmentRequest):
    """
    肺结节分割：读取 src_nrrd_path，推理后写掩码到 out_nrrd_path，返回 JSON。
    """
    # ---- 模型未加载 ----
    if not _state["model_loaded"] or _state["model"] is None:
        return _err(503, 5002, f"模型权重未加载，无法推理（{_state['load_error'] or '原因未知'}）")

    # ---- 源文件校验 ----
    if not os.path.isfile(body.src_nrrd_path):
        return _err(400, 4001, f"源文件不存在: {body.src_nrrd_path}")

    # ---- 并发保护 ----
    if _inference_lock.locked():
        return _err(503, 5003, "推理服务繁忙，已有任务在执行，请稍后重试")

    async with _inference_lock:
        if not _state["model_loaded"] or _state["model"] is None:
            return _err(503, 5002, "模型权重未加载，无法推理")

        try:
            t_total_start = time.time()

            # ---- 预处理 + 推理（两种后端分支，产出统一变量供后续复用） ----
            if _state["backend"] == "nnunet":
                from .nnunet_backend import run_nnunet_inference

                try:
                    infer_result = await asyncio.to_thread(
                        run_nnunet_inference, _state["model"], body.src_nrrd_path
                    )
                except Exception as e:
                    return _err(500, 5001, f"nnU-Net 推理失败: {type(e).__name__}: {e}")

                binary_mask = infer_result["binary_mask"]
                prob_map = infer_result["prob_map"]
                hu_arr = infer_result["hu_arr"]
                resampled_image = infer_result["sitk_image"]
                inference_ms = infer_result["inference_ms"]
            else:
                # ---- 预处理 ----
                try:
                    volume_tensor, resampled_image = await asyncio.to_thread(
                        load_and_preprocess, body.src_nrrd_path
                    )
                    hu_arr, _ = await asyncio.to_thread(
                        get_original_hu_array, body.src_nrrd_path
                    )
                except Exception as e:
                    return _err(422, 4221, f"文件预处理失败: {e}")

                # ---- 推理（在线程池避免阻塞事件循环） ----
                try:
                    infer_result = await asyncio.to_thread(
                        run_inference, _state["model"], volume_tensor
                    )
                except Exception as e:
                    return _err(500, 5001, f"推理失败: {type(e).__name__}: {e}")

                binary_mask = infer_result["binary_mask"]
                prob_map = infer_result["prob_map"]
                inference_ms = infer_result["inference_ms"]

            # ---- 写掩码 NRRD ----
            try:
                out_dir = os.path.dirname(body.out_nrrd_path)
                if out_dir:
                    os.makedirs(out_dir, exist_ok=True)

                mask_sitk = sitk.GetImageFromArray(binary_mask.astype(np.uint8))
                mask_sitk.CopyInformation(resampled_image)
                sitk.WriteImage(mask_sitk, body.out_nrrd_path)
            except Exception as e:
                return _err(500, 5001, f"写掩码文件失败: {e}")

            # ---- 连通域后处理 ----
            try:
                total_ms = int(round((time.time() - t_total_start) * 1000))
                lesions, summary = await asyncio.to_thread(
                    extract_lesions,
                    binary_mask,
                    prob_map,
                    hu_arr,
                    resampled_image,
                    total_ms,
                    _state["model_version"],
                )
            except Exception as e:
                lesions = []
                summary = {
                    "lesionCount": 0,
                    "maxDiameterMm": 0.0,
                    "totalVolumeMm3": 0.0,
                    "totalVolumeCm3": 0.0,
                    "overallRiskLevel": "低风险",
                    "modelVersion": _state["model_version"],
                    "processingTimeMs": inference_ms,
                    "note": f"后处理失败: {e}，建议安装 scikit-image",
                }

            # ---- 生成 meta（与 ct-viewer-algo 保持格式一致） ----
            D, H, W = binary_mask.shape
            sp = resampled_image.GetSpacing()  # (x, y, z)
            meta = {
                "shape_zyx": [D, H, W],
                "size_xyz": [W, H, D],
                "spacing_xyz": [round(sp[0], 4), round(sp[1], 4), round(sp[2], 4)],
                "min": 0,
                "max": 1,
                "is_mask": True,
                "source_name": (
                    (body.source_name or os.path.basename(body.src_nrrd_path))
                    + " | AI Lung Nodule Segmentation"
                ),
            }

            source_name = body.source_name or os.path.basename(body.src_nrrd_path)
            lesion_count = summary.get("lesionCount", 0)
            message = (
                f"AI 肺结节分割完成：检出 {lesion_count} 处疑似病灶，"
                f"处理耗时 {summary.get('processingTimeMs', 0)} ms"
            )

            return _ok({
                "is_mask": True,
                "meta": meta,
                "lesions": lesions,
                "summary": summary,
                "message": message,
            })

        except Exception as e:
            return _err(500, 5001, f"分割服务内部错误: {type(e).__name__}: {e}\n{traceback.format_exc()}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=config.SERVICE_HOST,
        port=config.SERVICE_PORT,
        reload=False,
    )
