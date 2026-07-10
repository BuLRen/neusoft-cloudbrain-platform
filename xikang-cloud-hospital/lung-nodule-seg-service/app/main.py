"""
lung-nodule-seg-service — 肺结节 AI 分割推理服务（FastAPI）

支持多个可在运行时按 model_id 任选的推理模型（服务启动时尝试加载全部）：
  - "monai"  ：本仓库 training/ 自训练的轻量 3D UNet（默认）
  - "segnet" ：移植自 Ola-Vish/lung-tumor-segmentation 的 2D SegNet（轻量演示模型）
  - "nnunet" ：官方 nnU-Net v2 框架权重（体积较大，默认不启用，见 app/config.py）

路由：
  GET  /health               健康检查 + 可用模型列表（裸 JSON，不走统一格式）
  POST /internal/segment     肺结节分割（JSON body，与 ct-viewer-algo 相同风格）

API 设计（与 ct-viewer-algo 保持一致）：
  请求 body: {src_nrrd_path, out_nrrd_path, source_name, model_id}
  服务读取 src_nrrd_path 处的 NRRD，用 model_id 指定的模型推理（未指定时用
  DEFAULT_MODEL_ID），把掩码写到 out_nrrd_path，返回
  {is_mask, meta, lesions, summary, message} 的 JSON。

并发保护：asyncio.Lock（同时只跑一个推理，无论用的是哪个模型）。
"""

from __future__ import annotations

import asyncio
import gc
import os
import time
import traceback
from contextlib import asynccontextmanager
from typing import Any, Callable, Dict, Optional

import numpy as np
import SimpleITK as sitk
import torch
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from . import config
from .model import build_model
from .preprocess import load_and_preprocess, get_original_hu_array
from .inference import run_inference
from .postprocess import extract_lesions


# ========================
# 全局状态
# ========================
# _state["models"][model_id] = {
#   "backend": "monai" | "segnet" | "nnunet",
#   "label": str, "version": str, "device": str,
#   "model": <加载后的模型/predictor 对象> | None,
#   "loaded": bool, "error": str | None,
# }
_state: dict = {
    "models": {},
    "default_model_id": config.DEFAULT_MODEL_ID,
    "inference_running": False,
    "inference_phase": None,
    "inference_started_at": None,
    "inference_source": None,
    "inference_model_id": None,
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


def _set_inference_phase(phase: str) -> None:
    _state["inference_phase"] = phase
    started_at = _state.get("inference_started_at")
    elapsed = int(time.time() - started_at) if started_at else 0
    print(f"[lung-nodule-seg-service] AI 分割阶段: {phase} | elapsed={elapsed}s", flush=True)


def _clear_inference_status() -> None:
    _state["inference_running"] = False
    _state["inference_phase"] = None
    _state["inference_started_at"] = None
    _state["inference_source"] = None
    _state["inference_model_id"] = None


# ========================
# monai 后端模型加载（本仓库自训练权重）
# ========================
def _load_monai_model() -> tuple[Optional[torch.nn.Module], Optional[str]]:
    if not os.path.isfile(config.MODEL_PATH):
        return None, f"权重文件不存在: {config.MODEL_PATH}"
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
        return model, None
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def _register_model(
    model_id: str,
    backend: str,
    version: str,
    device: str,
    model_obj: Any,
    error: Optional[str],
) -> None:
    meta = config.MODEL_REGISTRY_META.get(backend, {})
    _state["models"][model_id] = {
        "backend": backend,
        "label": meta.get("label", model_id),
        "description": meta.get("description", ""),
        "version": version,
        "device": device,
        "model": model_obj,
        "loaded": model_obj is not None,
        "error": error,
    }


def _load_all_models() -> None:
    # ---- monai：本仓库自训练模型（始终尝试加载） ----
    model, err = _load_monai_model()
    _register_model("monai", "monai", config.MODEL_VERSION, config.DEVICE, model, err)
    if model is not None:
        print(f"[lung-nodule-seg-service] monai 模型加载成功: {config.MODEL_PATH}")
    else:
        print(f"[lung-nodule-seg-service] monai 模型未加载（服务仍可启动）: {err}")

    # ---- segnet：Ola-Vish/lung-tumor-segmentation 移植模型（始终尝试加载） ----
    try:
        from .segnet_backend import load_model as load_segnet_model

        segnet_model, segnet_err = load_segnet_model()
    except Exception as e:
        segnet_model, segnet_err = None, f"{type(e).__name__}: {e}"
    _register_model(
        "segnet", "segnet", config.SEGNET_MODEL_VERSION, config.SEGNET_DEVICE, segnet_model, segnet_err
    )
    if segnet_model is not None:
        print(f"[lung-nodule-seg-service] segnet 模型加载成功: {config.SEGNET_MODEL_PATH}")
    else:
        print(f"[lung-nodule-seg-service] segnet 模型未加载（服务仍可启动）: {segnet_err}")

    # ---- nnunet：官方 nnU-Net v2（默认不启用，见 config.ENABLE_NNUNET） ----
    if config.ENABLE_NNUNET:
        try:
            from .nnunet_backend import load_predictor

            predictor, nnunet_err = load_predictor()
        except Exception as e:
            predictor, nnunet_err = None, f"{type(e).__name__}: {e}"
        _register_model(
            "nnunet", "nnunet", config.NNUNET_MODEL_VERSION, config.NNUNET_DEVICE, predictor, nnunet_err
        )
        if predictor is not None:
            print(f"[lung-nodule-seg-service] nnunet 模型加载成功: {config.NNUNET_MODEL_VERSION}")
        else:
            print(f"[lung-nodule-seg-service] nnunet 模型未加载（服务仍可启动）: {nnunet_err}")
    else:
        print("[lung-nodule-seg-service] nnunet 后端未启用（LUNG_NODULE_SEG_ENABLE_NNUNET=0），跳过加载")

    if config.DEFAULT_MODEL_ID not in _state["models"]:
        # DEFAULT_MODEL_ID 指向未注册的模型（例如 nnunet 未启用），仍记录一个
        # loaded=false 的占位条目，避免 /health、/internal/segment 里 KeyError。
        meta = config.MODEL_REGISTRY_META.get(config.DEFAULT_MODEL_ID, {})
        _state["models"][config.DEFAULT_MODEL_ID] = {
            "backend": config.DEFAULT_MODEL_ID,
            "label": meta.get("label", config.DEFAULT_MODEL_ID),
            "description": meta.get("description", ""),
            "version": config.ACTIVE_MODEL_VERSION,
            "device": config.DEVICE,
            "model": None,
            "loaded": False,
            "error": "该模型未启用，请检查相关环境变量配置",
        }


@asynccontextmanager
async def lifespan(app: FastAPI):
    _load_all_models()
    yield
    for entry in _state["models"].values():
        entry["model"] = None
        entry["loaded"] = False


# ========================
# FastAPI app
# ========================
app = FastAPI(
    title="lung-nodule-seg-service",
    description=(
        f"肺结节 AI 分割推理服务（默认模型: {config.DEFAULT_MODEL_ID}，"
        f"支持运行时通过 model_id 切换多个模型，基于 Task06_Lung 训练）"
    ),
    version="2.0.0",
    lifespan=lifespan,
)


# ========================
# 推理分发：统一 (model_obj, file_path) -> dict 契约
# ========================
def _run_monai_backend(model: torch.nn.Module, file_path: str) -> Dict[str, Any]:
    volume_tensor, resampled_image = load_and_preprocess(file_path)
    hu_arr, _ = get_original_hu_array(file_path)
    result = run_inference(model, volume_tensor)
    result["hu_arr"] = hu_arr
    result["sitk_image"] = resampled_image
    return result


def _run_segnet_backend(model: torch.nn.Module, file_path: str) -> Dict[str, Any]:
    from .segnet_backend import run_segnet_inference

    return run_segnet_inference(model, file_path)


def _run_nnunet_backend(predictor: Any, file_path: str) -> Dict[str, Any]:
    from .nnunet_backend import run_nnunet_inference

    return run_nnunet_inference(predictor, file_path)


_INFERENCE_DISPATCH: Dict[str, Callable[[Any, str], Dict[str, Any]]] = {
    "monai": _run_monai_backend,
    "segnet": _run_segnet_backend,
    "nnunet": _run_nnunet_backend,
}


# ========================
# 路由
# ========================
@app.get("/health")
async def health():
    started_at = _state.get("inference_started_at")
    elapsed_seconds = int(time.time() - started_at) if started_at else 0

    models = _state["models"]
    default_id = _state["default_model_id"]
    default_entry = models.get(default_id, {})
    any_loaded = any(entry.get("loaded") for entry in models.values())

    available_models = [
        {
            "id": model_id,
            "label": entry.get("label"),
            "description": entry.get("description"),
            "version": entry.get("version"),
            "backend": entry.get("backend"),
            "device": entry.get("device"),
            "loaded": entry.get("loaded", False),
            "error": entry.get("error"),
        }
        for model_id, entry in models.items()
    ]

    return {
        # ---- 兼容旧版字段（单模型时代），映射到“默认模型”的状态 ----
        "ok": any_loaded,
        "model_loaded": any_loaded,
        "backend": default_id,
        "device": default_entry.get("device"),
        "model_version": default_entry.get("version"),
        "load_error": default_entry.get("error"),
        # ---- 新增：多模型信息 ----
        "default_model_id": default_id,
        "available_models": available_models,
        # ---- 推理中状态（全局唯一，无论用的是哪个模型） ----
        "inference_running": _state["inference_running"],
        "inference_phase": _state["inference_phase"],
        "inference_elapsed_seconds": elapsed_seconds,
        "inference_source": _state["inference_source"],
        "inference_model_id": _state["inference_model_id"],
    }


class SegmentRequest(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    src_nrrd_path: str
    out_nrrd_path: str
    source_name: str = ""
    model_id: Optional[str] = None
    params: Dict[str, Any] = Field(default_factory=dict)


@app.post("/internal/segment")
async def internal_segment(body: SegmentRequest):
    """
    肺结节分割：读取 src_nrrd_path，用 model_id 指定的模型推理后写掩码到
    out_nrrd_path，返回 JSON。未指定 model_id 时使用 DEFAULT_MODEL_ID。
    """
    model_id = (body.model_id or _state["default_model_id"] or "").strip().lower()
    entry = _state["models"].get(model_id)

    # ---- 未知模型 ----
    if entry is None:
        available = list(_state["models"].keys())
        return _err(400, 4002, f"未知模型 model_id={model_id!r}，可用模型: {available}")

    # ---- 模型未加载 ----
    if not entry["loaded"] or entry["model"] is None:
        return _err(
            503, 5002,
            f"模型「{entry.get('label', model_id)}」权重未加载，无法推理（{entry.get('error') or '原因未知'}）",
        )

    # ---- 源文件校验 ----
    if not os.path.isfile(body.src_nrrd_path):
        return _err(400, 4001, f"源文件不存在: {body.src_nrrd_path}")

    # ---- 并发保护 ----
    if _inference_lock.locked():
        return _err(503, 5003, "推理服务繁忙，已有任务在执行，请稍后重试")

    async with _inference_lock:
        if not entry["loaded"] or entry["model"] is None:
            return _err(503, 5002, "模型权重未加载，无法推理")

        _state["inference_running"] = True
        _state["inference_started_at"] = time.time()
        _state["inference_source"] = body.source_name or os.path.basename(body.src_nrrd_path)
        _state["inference_model_id"] = model_id
        _set_inference_phase(f"接收请求，准备读取 CT 体数据（模型: {entry['label']}）")
        try:
            t_total_start = time.time()

            # ---- 预处理 + 推理（按 model_id 分发到对应后端） ----
            infer_fn = _INFERENCE_DISPATCH.get(entry["backend"])
            if infer_fn is None:
                return _err(500, 5001, f"未实现的推理后端: {entry['backend']}")

            try:
                _set_inference_phase(f"「{entry['label']}」推理中")
                infer_result = await asyncio.to_thread(infer_fn, entry["model"], body.src_nrrd_path)
            except Exception as e:
                return _err(500, 5001, f"{entry['label']} 推理失败: {type(e).__name__}: {e}")

            binary_mask = infer_result["binary_mask"]
            prob_map = infer_result["prob_map"]
            hu_arr = infer_result["hu_arr"]
            resampled_image = infer_result["sitk_image"]
            inference_ms = infer_result["inference_ms"]
            infer_result = None

            # ---- 写掩码 NRRD ----
            try:
                _set_inference_phase("写入 AI 分割掩码")
                out_dir = os.path.dirname(body.out_nrrd_path)
                if out_dir:
                    os.makedirs(out_dir, exist_ok=True)

                mask_sitk = sitk.GetImageFromArray(binary_mask.astype(np.uint8))
                mask_sitk.CopyInformation(resampled_image)
                sitk.WriteImage(mask_sitk, body.out_nrrd_path)
                mask_sitk = None
            except Exception as e:
                return _err(500, 5001, f"写掩码文件失败: {e}")

            # ---- 连通域后处理 ----
            try:
                _set_inference_phase("提取病灶与计算指标")
                total_ms = int(round((time.time() - t_total_start) * 1000))
                # 逐 2D 切片模型（如 segnet）缺乏 3D 上下文约束，噪点明显更多，
                # 需按 backend 收紧过滤规则，详见 config.POSTPROCESS_OVERRIDES。
                overrides = config.POSTPROCESS_OVERRIDES.get(entry["backend"], {})
                lesions, summary = await asyncio.to_thread(
                    extract_lesions,
                    binary_mask,
                    prob_map,
                    hu_arr,
                    resampled_image,
                    total_ms,
                    entry["version"],
                    min_voxels=overrides.get("min_voxels"),
                    min_volume_mm3=overrides.get("min_volume_mm3"),
                    min_confidence=overrides.get("min_confidence", 0.0),
                    min_slice_span=overrides.get("min_slice_span", 1),
                    morph_opening_radius=overrides.get("morph_opening_radius", 0),
                )
            except Exception as e:
                lesions = []
                summary = {
                    "lesionCount": 0,
                    "maxDiameterMm": 0.0,
                    "totalVolumeMm3": 0.0,
                    "totalVolumeCm3": 0.0,
                    "overallRiskLevel": "低风险",
                    "modelVersion": entry["version"],
                    "processingTimeMs": inference_ms,
                    "note": f"后处理失败: {e}，建议安装 scikit-image",
                }

            prob_map = None
            hu_arr = None
            if config.FORCE_GC_AFTER_INFERENCE:
                gc.collect()

            # ---- 生成 meta（与 ct-viewer-algo 保持格式一致） ----
            _set_inference_phase("整理返回结果")
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

            lesion_count = summary.get("lesionCount", 0)
            message = (
                f"AI 肺结节分割完成（模型: {entry['label']}）：检出 {lesion_count} 处疑似病灶，"
                f"处理耗时 {summary.get('processingTimeMs', 0)} ms"
            )

            response = _ok({
                "is_mask": True,
                "meta": meta,
                "lesions": lesions,
                "summary": summary,
                "message": message,
            })
            binary_mask = None
            resampled_image = None
            if config.FORCE_GC_AFTER_INFERENCE:
                gc.collect()
            return response

        except Exception as e:
            return _err(500, 5001, f"分割服务内部错误: {type(e).__name__}: {e}\n{traceback.format_exc()}")
        finally:
            _clear_inference_status()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=config.SERVICE_HOST,
        port=config.SERVICE_PORT,
        reload=False,
    )
