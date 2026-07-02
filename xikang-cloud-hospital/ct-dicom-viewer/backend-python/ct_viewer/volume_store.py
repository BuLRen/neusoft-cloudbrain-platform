"""内存体数据缓存（Spring Boot 迁移时可替换为 Redis / 数据库 + 文件存储）。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict

import SimpleITK as sitk


@dataclass
class VolumeRecord:
    image: sitk.Image
    is_mask: bool = False
    source_name: str = ""
    series_id: str = ""
    file_count: int = 0


class VolumeStore:
    def __init__(self) -> None:
        self._store: Dict[str, VolumeRecord] = {}

    def put(self, volume_id: str, record: VolumeRecord) -> None:
        self._store[volume_id] = record

    def get(self, volume_id: str) -> VolumeRecord | None:
        return self._store.get(volume_id)

    def delete(self, volume_id: str) -> None:
        self._store.pop(volume_id, None)
