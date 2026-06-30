from __future__ import annotations

import json
from pathlib import Path

import numpy as np

_STATS: dict[str, float] | None = None


def load_norm_stats() -> dict[str, float]:
    global _STATS
    if _STATS is not None:
        return _STATS
    path = Path(__file__).resolve().parents[1] / "models" / "norm_stats.json"
    if not path.exists():
        _STATS = {"glucose_mean": 0.0, "glucose_std": 1.0}
        return _STATS
    _STATS = json.loads(path.read_text(encoding="utf-8"))
    return _STATS


def normalize_glucose(values: list[float] | np.ndarray) -> list[float]:
    stats = load_norm_stats()
    mean = float(stats.get("glucose_mean", 0.0))
    std = float(stats.get("glucose_std", 1.0))
    arr = np.asarray(values, dtype=float)
    return ((arr - mean) / std).tolist()


def denormalize_glucose(values: list[float] | np.ndarray) -> list[float]:
    stats = load_norm_stats()
    mean = float(stats.get("glucose_mean", 0.0))
    std = float(stats.get("glucose_std", 1.0))
    arr = np.asarray(values, dtype=float)
    return (arr * std + mean).tolist()
