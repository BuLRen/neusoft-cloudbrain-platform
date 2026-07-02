# CT DICOM Viewer

独立的 CT 医学影像查看与滤波工作台，从 `jiqixtxi/day2` 与 `day3/web-ct-viewer` 中提取整理。

**适用场景**：本地开发调试、嵌入 Spring Boot 作为静态前端、或对接 Java 后端 REST API。

## 项目组成

| 目录 | 说明 |
|------|------|
| `frontend/` | Vue 3 + vtk.js 网页查看器（2D 三视图 + 3D 体渲染） |
| `backend-python/` | Flask + SimpleITK 参考后端（算法与 day2 桌面版一致） |
| `desktop/` | PyQt5 桌面版（`dicom_viewer_gui.py`，可选参考） |
| `docs/` | 架构说明、API 文档、Spring Boot 集成指南 |

## 功能

- DICOM 文件夹加载（自动选择切片数最多的 Series）
- NRRD / NIfTI 加载
- 轴状 / 冠状 / 矢状 2D 切片 + 窗宽窗位
- 3D 体渲染（vtk.js）
- 7 种滤波器（高斯、双边、中值、曲率流、各向异性扩散、金属伪影掩码）
- 保存切片 PNG、体数据 NRRD / NIfTI

## 快速启动

### 1. 后端（Python 参考实现）

```bash
cd backend-python
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python server.py
```

服务默认监听 `http://127.0.0.1:8000`。

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5174`。开发模式下 Vite 会将 `/api` 代理到后端。

## 构建生产包（供 Spring Boot 托管）

```bash
cd frontend
npm run build
```

产物在 `frontend/dist/`，可拷贝到 Spring Boot 的 `src/main/resources/static/`。

## 与 Spring Boot 集成

详见 [docs/SPRING_BOOT_INTEGRATION.md](docs/SPRING_BOOT_INTEGRATION.md)。

核心思路：

1. **前端**：`npm run build` 后静态资源由 Spring Boot 提供
2. **后端**：在 Java 中实现与 [docs/API.md](docs/API.md) 相同的 REST 接口
3. **医学图像处理**：可选 dcm4che + 自定义 Java 实现，或调用 Python 微服务 / JNI 封装 SimpleITK

## 文档

- [项目梳理与架构](docs/PROJECT_OVERVIEW.md)
- [REST API 规范](docs/API.md)
- [Spring Boot 集成指南](docs/SPRING_BOOT_INTEGRATION.md)

## 来源说明

- 桌面版原型：`day2/dicom_viewer_gui.py`（PyQt5 + VTK）
- 网页版迁移：`day3/web-ct-viewer`（Vue + vtk.js + Flask）
- 本仓库为上述代码的**独立、模块化**整理版本
