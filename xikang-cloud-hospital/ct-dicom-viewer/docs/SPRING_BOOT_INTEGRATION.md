# Spring Boot 集成指南

本文说明如何将 `ct-dicom-viewer` 嵌入 Spring Boot 项目。

## 方案概览

推荐 **前后端分离、同域部署**：

```text
Spring Boot (端口 8080)
├── /                    → 静态前端 (Vue build 产物)
├── /api/**              → Java Controller（替代 Flask）
└── /actuator/health     → 可选
```

前端通过相对路径 `/api` 调用后端，无需 CORS。

---

## 步骤 1：构建并拷贝前端

```bash
cd ct-dicom-viewer/frontend
npm install
npm run build
```

将 `frontend/dist/` 下所有文件复制到 Spring Boot：

```text
src/main/resources/static/
├── index.html
├── assets/
└── ...
```

若部署在子路径（如 `/ct-viewer/`），构建前设置：

```bash
# frontend/.env.production
VITE_BASE_PATH=/ct-viewer/
VITE_API_BASE=/api
```

并在 Spring Boot 中配置静态资源映射。

---

## 步骤 2：实现 REST API

参考 [API.md](./API.md)，创建 Controller：

```java
@RestController
@RequestMapping("/api")
public class CtVolumeController {

    @GetMapping("/health")
    public Map<String, Boolean> health() {
        return Map.of("ok", true);
    }

    @PostMapping("/load-dicom")
    public VolumeLoadResponse loadDicom(@RequestParam("files") MultipartFile[] files) {
        // 1. 写入临时目录
        // 2. 用 dcm4che 或调用 Python 服务读取 DICOM
        // 3. 转为 NRRD 缓存，返回 volume_id + meta
    }

    // ... 其余接口
}
```

### 后端实现路径选择

| 方案 | 优点 | 缺点 |
|------|------|------|
| **A. Java + dcm4che** | 纯 Java 栈，易运维 | 滤波算法需自行移植或用第三方库 |
| **B. Java 调 Python 微服务** | 算法与 `backend-python/ct_viewer/filters.py` 完全一致 | 多一个 Python 进程 |
| **C. JNI / SimpleITK Java** | 算法与 Python 版一致 | 部署复杂 |

**建议**：短期用方案 B（Flask 作 sidecar），长期逐步迁移滤波到 Java 或保留专用图像处理服务。

---

## 步骤 3：Spring Boot 配置示例

### 3.1 静态资源 + SPA 路由

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Vue Router history 模式时需要；当前项目用 hash 可省略
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
```

### 3.2 文件上传大小

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 2GB
```

DICOM 文件夹可能包含数百 MB 数据。

### 3.3 CORS（仅前后端分端口开发时需要）

生产同域部署不需要。开发时若前端 `npm run dev`、后端 Spring Boot：

```java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:5174");
        }
    };
}
```

---

## 步骤 4：体数据存储设计

Flask 参考实现使用内存 `VolumeStore`，Spring Boot 生产环境建议：

```text
volume_id (UUID)
    ├── meta  → Redis / DB
    └── nrrd  → 本地目录 / MinIO / OSS
```

伪代码：

```java
public class VolumeService {
    public String save(SitkImage image, VolumeMeta meta) {
        String id = UUID.randomUUID().toString();
        nrrdStorage.write(id, image);
        metaRepository.save(id, meta);
        return id;
    }

    public byte[] getNrrdBytes(String volumeId) {
        return nrrdStorage.read(volumeId);
    }
}
```

---

## 步骤 5：滤波算法迁移对照

Java 实现时对照 `backend-python/ct_viewer/filters.py`：

| 滤波器 | SimpleITK Python | Java 替代思路 |
|--------|------------------|---------------|
| 高斯 | SmoothingRecursiveGaussian | ITK Java / 自实现卷积 |
| 双边 | BilateralImageFilter | OpenCV Java |
| 中值 | MedianImageFilter | OpenCV Java |
| 曲率流 | CurvatureFlow | ITK 或保留 Python |
| 各向异性扩散 | CurvatureAnisotropicDiffusion | ITK 或保留 Python |
| 金属掩码 | 自定义形态学 + 连通域 | 移植 `filters.py` 逻辑 |

金属掩码逻辑见 `filters.py` 中 `_run_metal_mask_filter()`，与桌面版 `FilterWorker` 完全一致。

---

## 步骤 6：验证集成

1. 启动 Spring Boot，访问 `http://localhost:8080`
2. 页面应显示「后端已连接」（`/api/health` 返回 ok）
3. 上传 NRRD 或 DICOM，检查三视图与 3D 渲染
4. 执行滤波，确认 `volume_id` 更新且视图刷新

---

## 开发阶段双进程模式

在 Java 后端未完成前，可并行运行：

```bash
# 终端 1：Python 参考后端
cd backend-python && python server.py

# 终端 2：Vue 开发服务器（代理 /api → 8000）
cd frontend && npm run dev
```

Spring Boot 开发时可暂时反向代理 `/api` 到 Python：

```yaml
# 或使用 spring.cloud.gateway 转发
```

---

## 常见问题

**Q: 前端显示「后端未连接」**  
检查 `/api/health` 是否可达，以及 `VITE_API_BASE` 是否与 Spring Boot 路由前缀一致。

**Q: 3D 渲染黑屏**  
确认 `/api/volume/{id}/nrrd` 返回有效 NRRD，且为内嵌 gzip 编码。

**Q: DICOM 上传失败**  
增大 `multipart` 限制；确认临时目录有写权限。

**Q: 是否必须保留 Python 后端？**  
否。Python 仅为参考实现；Spring Boot 实现相同 API 后即可移除。
