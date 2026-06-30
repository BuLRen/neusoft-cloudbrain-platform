from __future__ import annotations

from pathlib import Path

import numpy as np

from app.config import HORIZON, N_FEATURES, SEQ_LEN
from app.norm import denormalize_glucose, load_norm_stats, normalize_glucose


def classify_risk(values: list[float]) -> str:
    if not values:
        return "unknown"
    mx, mn = max(values), min(values)
    if mx > 10.0 or mn < 3.9:
        return "high"
    if mx > 7.8:
        return "medium"
    return "low"


def build_feature_matrix(observations: list[dict]) -> np.ndarray | None:
    if len(observations) < SEQ_LEN:
        return None
    tail = observations[-SEQ_LEN:]
    mat = np.zeros((SEQ_LEN, N_FEATURES), dtype=np.float32)
    for i, o in enumerate(tail):
        mat[i, 0] = float(o.get("blood_glucose") or 0)
        mat[i, 1] = float(o.get("insulin_total") or 0)
        mat[i, 2] = float(o.get("meal_flag") or 0)
        mat[i, 3] = float(o.get("exercise_flag") or 0)
    stats = load_norm_stats()
    mat[:, 0] = (mat[:, 0] - stats.get("glucose_mean", 0.0)) / max(stats.get("glucose_std", 1.0), 1e-6)
    return mat


class GlucosePredictor:
    def __init__(self, model_path: Path):
        import onnxruntime as ort

        self.session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

    def predict(self, observations: list[dict]) -> tuple[list[float], float] | None:
        mat = build_feature_matrix(observations)
        if mat is None:
            return None
        inp = mat.reshape(1, SEQ_LEN, N_FEATURES)
        out = self.session.run(None, {self.input_name: inp})[0]
        values = denormalize_glucose([float(v) for v in out[0].tolist()[:HORIZON]])
        confidence = max(0.5, min(0.95, 1.0 - np.std(values) / 10.0))
        return values, confidence
