# ct-viewer-algo

内网无状态 CT 图像处理 worker（FastAPI + SimpleITK），仅供 `ct-viewer-service` 调用。

## 启动

```bash
cd ct-viewer-algo
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app.main
```

默认监听 `http://0.0.0.0:8106`。

## 内网 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/internal/convert` | DICOM/NRRD → NRRD |
| POST | `/internal/filter` | 滤波 |
| POST | `/internal/segment` | AI 病灶分割（掩码 + 病灶清单） |
| POST | `/internal/export` | 导出 nii/nii.gz |

响应格式：`{code, message, data}`。
