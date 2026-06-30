from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from src.config import DATA_PROCESSED


def load_norm_stats() -> dict[str, float]:
    path = DATA_PROCESSED / "norm_stats.json"
    if not path.exists():
        return {"glucose_mean": 0.0, "glucose_std": 1.0}
    return json.loads(path.read_text(encoding="utf-8"))


def normalize_windows(x: np.ndarray, stats: dict[str, float] | None = None) -> np.ndarray:
    stats = stats or load_norm_stats()
    mean = float(stats["glucose_mean"])
    std = max(float(stats["glucose_std"]), 1e-6)
    out = x.copy()
    out[:, :, 0] = (out[:, :, 0] - mean) / std
    return out


def normalize_targets(y: np.ndarray, stats: dict[str, float] | None = None) -> np.ndarray:
    stats = stats or load_norm_stats()
    mean = float(stats["glucose_mean"])
    std = max(float(stats["glucose_std"]), 1e-6)
    return (y - mean) / std


def denormalize_targets(y: np.ndarray, stats: dict[str, float] | None = None) -> np.ndarray:
    stats = stats or load_norm_stats()
    mean = float(stats["glucose_mean"])
    std = float(stats["glucose_std"])
    return y * std + mean
