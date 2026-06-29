"""
FastAPI 入口
============

路由：
  - POST /analyze   推理接口（multipart 上传 NIfTI）
  - GET  /health    健康检查（裸 JSON，不走统一格式）

模型加载：FastAPI lifespan，启动时一次性加载到 CPU 内存常驻。
        权重不存在时捕获异常，设 model_loaded=False，不让服务起不来。

并发保护：asyncio.Lock（MAX_CONCURRENT_INFERENCES=1），同时只跑一个推理。
"""

from __future__ import annotations

import asyncio
import os
import tempfile
from contextlib import asynccontextmanager
from typing import Optional

import torch
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse

from . import config
from .model import MultiTaskUNet3D
from .preprocess import load_and_preprocess
from .inference import run_inference


# ========================
# 全局状态
# ========================
_state: dict = {
    "model": None,              # Optional[torch.nn.Module]
    "model_loaded": False,
    "device": config.DEVICE,
    "load_error": None,         # str | None  权重加载失败的原因（仅供 /health 诊断）
}


# 并发锁：保证同时只有一个推理在跑（MAX_CONCURRENT_INFERENCES=1）
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
    """
    从 MODEL_PATH 加载权重。失败返回 None 并把异常信息记到 _state["load_error"]。

    权重缺失是预期情况（用户稍后下载），不应当作致命错误。
    """
    if not os.path.isfile(config.MODEL_PATH):
        _state["load_error"] = f"权重文件不存在: {config.MODEL_PATH}"
        return None

    try:
        model = MultiTaskUNet3D()
        # CPU 推理；map_location 兜底防止权重是在 GPU 机器上存的
        state_dict = torch.load(
            config.MODEL_PATH,
            map_location=config.DEVICE,
            weights_only=True,   # pytorch>=2.0 安全加载，只允许 state_dict
        )
        # 兼容两种保存格式：纯 state_dict 或 {"model": state_dict, ...}
        if isinstance(state_dict, dict) and "model" in state_dict \
                and not any(k.startswith("inc") for k in state_dict.keys()):
            sd = state_dict["model"]
        elif isinstance(state_dict, dict) and "state_dict" in state_dict \
                and not any(k.startswith("inc") for k in state_dict.keys()):
            sd = state_dict["state_dict"]
        else:
            sd = state_dict

        # 去掉可能存在的 'module.' 前缀（DataParallel 保存格式）
        clean_sd = {}
        for k, v in sd.items():
            nk = k[len("module."):] if k.startswith("module.") else k
            clean_sd[nk] = v

        model.load_state_dict(clean_sd)
        model.to(config.DEVICE)
        model.eval()
        _state["load_error"] = None
        return model
    except Exception as e:  # noqa: BLE001
        _state["load_error"] = f"{type(e).__name__}: {e}"
        return None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动钩子：加载模型；停止钩子：清理。"""
    model = _load_model()
    if model is not None:
        _state["model"] = model
        _state["model_loaded"] = True
        print(f"[ai-ct-service] 模型加载成功: {config.MODEL_PATH}")
    else:
        _state["model"] = None
        _state["model_loaded"] = False
        print(f"[ai-ct-service] 模型未加载（服务仍可启动）: {_state['load_error']}")

    yield

    # 释放
    _state["model"] = None
    _state["model_loaded"] = False


# ========================
# FastAPI app
# ========================
app = FastAPI(
    title="ai-ct-service",
    description="头部 CT 伪影检测推理服务（3D U-Net 多任务）",
    version="1.0.0",
    lifespan=lifespan,
)


# ========================
# 路由
# ========================
@app.get("/health")
async def health():
    """健康检查。裸 JSON，不走统一 {code,message,data} 格式（供 K8s/LB 用）。"""
    return {
        "status": "up",
        "model_loaded": _state["model_loaded"],
        "device": _state["device"],
    }


def _is_nifti_filename(filename: Optional[str]) -> bool:
    if not filename:
        return False
    name = filename.lower()
    return name.endswith(".nii") or name.endswith(".nii.gz")


@app.post("/analyze")
async def analyze(file: UploadFile = File(...)):
    """
    推理接口：multipart 上传 NIfTI，同步返回伪影检测结果。

    错误码：
      400 / 4001 —— 文件格式不是 NIfTI
      422 / 4221 —— NIfTI 解析失败
      503 / 5002 —— 模型权重未加载
      500 / 5001 —— 推理过程异常
      503 / 5003 —— 服务繁忙（已有推理在跑）
    """
    # ---- 模型未加载：明确提示，不让请求继续 ----
    if not _state["model_loaded"] or _state["model"] is None:
        return _err(
            http_status=503,
            code=5002,
            message=f"模型权重未加载，无法推理（{_state['load_error'] or '原因未知'}）",
        )

    # ---- 文件格式校验 ----
    if not _is_nifti_filename(file.filename):
        return _err(
            http_status=400,
            code=4001,
            message="文件格式无效，仅支持 NIfTI (.nii/.nii.gz)",
        )

    # ---- 并发保护：抢锁，抢不到立刻拒绝 ----
    if _inference_lock.locked():
        return _err(
            http_status=503,
            code=5003,
            message="服务繁忙，已有推理任务在执行，请稍后重试",
        )

    async with _inference_lock:
        # 拿到锁后再次确认模型状态（防止启动后权重被外部删除的极端情况）
        if not _state["model_loaded"] or _state["model"] is None:
            return _err(
                http_status=503, code=5002,
                message="模型权重未加载，无法推理",
            )

        # ---- 把上传内容落到临时文件（SimpleITK/monai 要读路径）----
        tmp_path = None
        try:
            suffix = ".nii.gz" if file.filename.lower().endswith(".gz") else ".nii"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                content = await file.read()
                tmp.write(content)
                tmp_path = tmp.name

            # ---- 预处理 ----
            try:
                volume = load_and_preprocess(tmp_path)
            except Exception as e:  # noqa: BLE001
                return _err(
                    http_status=422, code=4221,
                    message=f"NIfTI 文件解析失败: {e}",
                )

            # ---- 推理（CPU 慢，放到线程池避免阻塞事件循环）----
            try:
                result = await asyncio.to_thread(
                    run_inference, _state["model"], volume
                )
            except Exception as e:  # noqa: BLE001
                return _err(
                    http_status=500, code=5001,
                    message=f"推理失败: {type(e).__name__}: {e}",
                )

            return _ok(result)

        finally:
            # 清理临时文件
            if tmp_path and os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass


# ========================
# 直接运行入口（uvicorn app.main:app 不走这里；python -m app.main 走这里）
# ========================
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=config.SERVICE_HOST,
        port=config.SERVICE_PORT,
        reload=False,
    )
