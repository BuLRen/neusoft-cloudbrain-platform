"""ct-viewer-algo 配置（内网无状态图像处理 worker）。"""

from __future__ import annotations

import os

SERVICE_HOST = os.getenv("CT_VIEWER_ALGO_HOST", "0.0.0.0")
SERVICE_PORT = int(os.getenv("CT_VIEWER_ALGO_PORT", "8106"))
