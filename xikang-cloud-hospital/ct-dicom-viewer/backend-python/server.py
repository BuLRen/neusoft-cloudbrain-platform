"""Flask REST API — CT 体数据加载、滤波与导出（Spring Boot 迁移参考实现）。"""

from __future__ import annotations

import io
import os
import tempfile
import traceback
import uuid

import SimpleITK as sitk
from flask import Flask, jsonify, request, send_file
from flask_cors import CORS

from ct_viewer import (
    VolumeRecord,
    VolumeStore,
    image_meta,
    read_dicom_series,
    run_filter,
    serialize_image_to_nrrd_bytes,
)

app = Flask(__name__)
CORS(app)

volume_store = VolumeStore()


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"ok": True})


@app.route("/api/load-nrrd", methods=["POST"])
def load_nrrd():
    if "file" not in request.files:
        return jsonify({"error": "缺少文件 file"}), 400

    file = request.files["file"]
    if not file.filename:
        return jsonify({"error": "文件名为空"}), 400

    suffix = os.path.splitext(file.filename)[1] or ".nrrd"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        tmp_path = tmp.name
        file.save(tmp_path)

    try:
        image = sitk.ReadImage(tmp_path)
        volume_id = str(uuid.uuid4())
        record = VolumeRecord(image=image, source_name=file.filename)
        volume_store.put(volume_id, record)
        return jsonify({"volume_id": volume_id, "meta": image_meta(image, record)})
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@app.route("/api/load-dicom", methods=["POST"])
def load_dicom():
    files = request.files.getlist("files")
    if not files:
        return jsonify({"error": "请上传 DICOM 文件夹内容"}), 400

    with tempfile.TemporaryDirectory() as tmpdir:
        for index, f in enumerate(files):
            name = f.filename or f"dicom_{index}.dcm"
            safe_name = os.path.basename(name)
            out_path = os.path.join(tmpdir, f"{index:04d}_{safe_name}")
            f.save(out_path)

        image, series_id, file_count = read_dicom_series(tmpdir)
        volume_id = str(uuid.uuid4())
        record = VolumeRecord(
            image=image,
            source_name="DICOM Folder",
            series_id=series_id,
            file_count=file_count,
        )
        volume_store.put(volume_id, record)
        return jsonify({"volume_id": volume_id, "meta": image_meta(image, record)})


@app.route("/api/volume/<volume_id>/nrrd", methods=["GET"])
def get_volume_nrrd(volume_id):
    record = volume_store.get(volume_id)
    if not record:
        return jsonify({"error": "volume_id 不存在"}), 404

    payload = serialize_image_to_nrrd_bytes(record.image)
    return send_file(
        io.BytesIO(payload),
        mimetype="application/octet-stream",
        as_attachment=False,
        download_name=f"{volume_id}.nrrd",
    )


@app.route("/api/filter", methods=["POST"])
def apply_filter():
    data = request.get_json(force=True, silent=True) or {}
    source_volume_id = data.get("source_volume_id")
    filter_name = data.get("filter_name")
    params = data.get("params", {})

    if not source_volume_id or not filter_name:
        return jsonify({"error": "缺少 source_volume_id 或 filter_name"}), 400

    source_record = volume_store.get(source_volume_id)
    if not source_record:
        return jsonify({"error": "source_volume_id 不存在"}), 404

    try:
        result_image, is_mask = run_filter(source_record.image, filter_name, params)
        result_id = str(uuid.uuid4())
        result_record = VolumeRecord(
            image=result_image,
            is_mask=is_mask,
            source_name=f"{source_record.source_name} | {filter_name}",
            series_id=source_record.series_id,
            file_count=source_record.file_count,
        )
        volume_store.put(result_id, result_record)
        return jsonify(
            {
                "volume_id": result_id,
                "is_mask": is_mask,
                "message": f"滤波完成：{filter_name}",
                "meta": image_meta(result_image, result_record),
            }
        )
    except Exception as exc:
        return jsonify({"error": str(exc), "traceback": traceback.format_exc()}), 500


@app.route("/api/volume/<volume_id>/save", methods=["GET"])
def save_volume(volume_id):
    fmt = request.args.get("format", "nrrd")
    record = volume_store.get(volume_id)
    if not record:
        return jsonify({"error": "volume_id 不存在"}), 404

    suffix = ".nrrd"
    if fmt in ("nii", "nii.gz"):
        suffix = ".nii.gz"

    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
        out_path = tmp.name

    try:
        sitk.WriteImage(record.image, out_path)
        with open(out_path, "rb") as f:
            payload = f.read()
    finally:
        if os.path.exists(out_path):
            os.remove(out_path)

    return send_file(
        io.BytesIO(payload),
        mimetype="application/octet-stream",
        as_attachment=True,
        download_name=f"filtered_volume{suffix}",
    )


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    app.run(host="127.0.0.1", port=port, debug=True)
