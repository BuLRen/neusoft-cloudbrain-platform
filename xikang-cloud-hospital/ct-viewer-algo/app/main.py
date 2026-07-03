"""
ct-viewer-algo — 内网无状态 CT 图像处理 worker（FastAPI + SimpleITK）

仅被 ct-viewer-service (Java) 调用，不对公网暴露。
"""

from __future__ import annotations

import os
import traceback
from typing import Any, Optional

import SimpleITK as sitk
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from ct_viewer import image_meta, read_dicom_series, read_volume_file, run_filter, write_image_to_nrrd

from . import config


def _ok(data: dict) -> dict:
    return {"code": 200, "message": "success", "data": data}


def _err(http_status: int, code: int, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=http_status,
        content={"code": code, "message": message, "data": None},
    )


class ConvertRequest(BaseModel):
    kind: str = Field(..., description="dicom | nrrd | nii")
    src_path: str
    out_nrrd_path: str
    source_name: str = ""
    series_id: str = ""
    file_count: int = 0


class FilterRequest(BaseModel):
    src_nrrd_path: str
    out_nrrd_path: str
    filter_name: str
    params: dict[str, Any] = Field(default_factory=dict)
    source_name: str = ""


class ExportRequest(BaseModel):
    src_nrrd_path: str
    out_path: str
    format: str = "nrrd"


app = FastAPI(title="ct-viewer-algo", version="1.0.0")


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/internal/convert")
def internal_convert(body: ConvertRequest):
    try:
        kind = body.kind.lower().strip()
        if kind == "dicom":
            if not os.path.isdir(body.src_path):
                return _err(400, 400, f"DICOM 目录不存在：{body.src_path}")
            image, series_id, file_count = read_dicom_series(body.src_path)
            source_name = body.source_name or "DICOM Folder"
            series_id_val = series_id
            file_count_val = file_count
        elif kind in ("nrrd", "nii", "nifti"):
            image = read_volume_file(body.src_path)
            source_name = body.source_name or os.path.basename(body.src_path)
            series_id_val = body.series_id
            file_count_val = body.file_count
        else:
            return _err(400, 400, f"不支持的 kind：{body.kind}")

        write_image_to_nrrd(image, body.out_nrrd_path)
        meta = image_meta(
            image,
            source_name=source_name,
            is_mask=False,
            series_id=series_id_val,
            file_count=file_count_val,
        )
        return _ok({"meta": meta})
    except Exception as exc:
        return _err(500, 500, f"{exc}\n{traceback.format_exc()}")


@app.post("/internal/filter")
def internal_filter(body: FilterRequest):
    try:
        if not os.path.isfile(body.src_nrrd_path):
            return _err(400, 400, f"源 NRRD 不存在：{body.src_nrrd_path}")

        image = read_volume_file(body.src_nrrd_path)
        result_image, is_mask = run_filter(image, body.filter_name, body.params or {})
        write_image_to_nrrd(result_image, body.out_nrrd_path)

        label = body.filter_name
        source_name = body.source_name or os.path.basename(body.src_nrrd_path)
        meta = image_meta(
            result_image,
            source_name=f"{source_name} | {label}",
            is_mask=is_mask,
        )
        return _ok({"is_mask": is_mask, "meta": meta, "message": f"滤波完成：{label}"})
    except Exception as exc:
        return _err(500, 500, f"{exc}\n{traceback.format_exc()}")


@app.post("/internal/export")
def internal_export(body: ExportRequest):
    try:
        if not os.path.isfile(body.src_nrrd_path):
            return _err(400, 400, f"源 NRRD 不存在：{body.src_nrrd_path}")

        image = read_volume_file(body.src_nrrd_path)
        fmt = (body.format or "nrrd").lower().strip()
        out_path = body.out_path

        parent = os.path.dirname(out_path)
        if parent:
            os.makedirs(parent, exist_ok=True)

        if fmt == "nrrd":
            sitk.WriteImage(image, out_path)
        elif fmt in ("nii", "nii.gz", "nifti"):
            sitk.WriteImage(image, out_path)
        else:
            return _err(400, 400, f"不支持的导出格式：{body.format}")

        return _ok({"path": out_path})
    except Exception as exc:
        return _err(500, 500, f"{exc}\n{traceback.format_exc()}")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=config.SERVICE_HOST,
        port=config.SERVICE_PORT,
        reload=False,
    )
