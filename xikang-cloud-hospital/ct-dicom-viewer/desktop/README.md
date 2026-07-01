# 桌面版 CT 滤波可视化工具

PyQt5 + VTK + SimpleITK 桌面应用，原始代码来自 `day2/dicom_viewer_gui.py`。

## 依赖

```bash
cd desktop
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

需要系统已安装 VTK 与 PyQt5 兼容环境。

## 运行

```bash
python dicom_viewer_gui.py
```

## 与网页版关系

- 算法逻辑与 `backend-python/ct_viewer/filters.py` 一致
- 3D 渲染使用原生 VTK（非 vtk.js）
- 适合本地离线使用；Web / Spring Boot 集成请使用 `frontend/` + API 后端
