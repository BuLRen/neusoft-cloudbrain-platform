# REST API 规范

Spring Boot 后端需实现以下接口，保持与前端 `frontend/src/lib/apiClient.js` 兼容。

**Base URL**：`/api`（与前端 `VITE_API_BASE` 一致）

---

## GET /api/health

健康检查。

**响应 200**

```json
{ "ok": true }
```

---

## POST /api/load-nrrd

上传 NRRD 或 NIfTI 文件。

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | `.nrrd` / `.nii` / `.nii.gz` |

**响应 200**

```json
{
  "volume_id": "uuid-string",
  "meta": {
    "shape_zyx": [512, 512, 300],
    "size_xyz": [512, 512, 300],
    "spacing_xyz": [0.5, 0.5, 1.0],
    "min": -1024.0,
    "max": 3071.0,
    "is_mask": false,
    "source_name": "ct.nrrd",
    "series_id": "",
    "file_count": 0
  }
}
```

---

## POST /api/load-dicom

上传 DICOM 文件夹内全部文件。

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| files | File[] | 多个 DICOM 文件 |

**响应 200**：同 `load-nrrd`，`series_id` 与 `file_count` 有值。

**业务逻辑**：

1. 将文件写入临时目录
2. 调用 GDCM/SimpleITK 读取所有 Series
3. 选择**切片数量最多**的 Series

---

## GET /api/volume/{volume_id}/nrrd

获取体数据 NRRD 二进制（供前端 vtk.js 解析）。

**响应 200**

- Content-Type: `application/octet-stream`
- Body: NRRD 文件字节流

**响应 404**

```json
{ "error": "volume_id 不存在" }
```

---

## POST /api/filter

对源体数据执行滤波。

**Content-Type**：`application/json`

```json
{
  "source_volume_id": "uuid",
  "filter_name": "高斯滤波 Gaussian",
  "params": {
    "spatial_sigma": 1.0,
    "range_sigma": 50.0,
    "median_radius": 1,
    "iterations": 5,
    "time_step": 0.0625,
    "conductance": 3.0,
    "metal_threshold_lower": 1000,
    "metal_threshold_upper": 4000,
    "metal_gradient_threshold": 100,
    "metal_opening_radius": 1,
    "metal_closing_radius": 2,
    "metal_min_component_size": 50
  }
}
```

### filter_name 枚举

| 值 | params 有效字段 |
|----|----------------|
| 无滤波 | — |
| 高斯滤波 Gaussian | spatial_sigma |
| 双边滤波 Bilateral | spatial_sigma, range_sigma |
| 中值滤波 Median | median_radius |
| 曲率流平滑 Curvature Flow | iterations, time_step |
| 各向异性扩散 Anisotropic Diffusion | iterations, time_step, conductance |
| 金属伪影掩码 Metal Artifact Mask | metal_* 全部字段 |

**响应 200**

```json
{
  "volume_id": "new-uuid",
  "is_mask": false,
  "message": "滤波完成：高斯滤波 Gaussian",
  "meta": { "...": "同 load-nrrd meta" }
}
```

金属掩码时 `is_mask: true`。

---

## GET /api/volume/{volume_id}/save

下载体数据文件。

**Query**

| 参数 | 默认 | 说明 |
|------|------|------|
| format | nrrd | `nrrd` / `nii.gz` / `nii` |

**响应 200**

- Content-Disposition: attachment
- Body: 对应格式文件字节流

---

## 错误响应格式

```json
{
  "error": "错误描述",
  "traceback": "可选，仅开发环境"
}
```

HTTP 状态码：400（参数错误）、404（volume 不存在）、500（处理失败）

---

## volume_id 生命周期

- 每次 load / filter 成功返回新 `volume_id`
- 服务端需缓存体数据直至过期或主动清理
- 生产环境建议：Redis 存元信息 + MinIO/OSS 存 NRRD 文件
