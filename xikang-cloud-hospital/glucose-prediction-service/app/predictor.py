from __future__ import annotations

from datetime import datetime, timedelta
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


def resample_hourly(observations: list[dict], seq_len: int = SEQ_LEN) -> list[dict]:
    """Resample sparse observations to consecutive hourly slots (patient values win on conflict)."""
    if not observations:
        return []
    sorted_obs = sorted(observations, key=lambda o: o["observed_at"])
    anchor = datetime.fromisoformat(sorted_obs[-1]["observed_at"].replace("Z", "+00:00").replace(" ", "T")[:19])
    anchor = anchor.replace(minute=0, second=0, microsecond=0)

    by_hour: dict[str, dict] = {}
    for obs in sorted_obs:
        ts = datetime.fromisoformat(obs["observed_at"].replace("Z", "+00:00").replace(" ", "T")[:19])
        hour_key = ts.replace(minute=0, second=0, microsecond=0).strftime("%Y-%m-%dT%H:%M:%S")
        by_hour[hour_key] = obs

    glucose = float(sorted_obs[-1].get("blood_glucose") or 7.5)
    insulin = float(sorted_obs[-1].get("insulin_total") or 0.0)
    meal = float(sorted_obs[-1].get("meal_flag") or 0.0)
    exercise = float(sorted_obs[-1].get("exercise_flag") or 0.0)

    series: list[dict] = []
    for offset in range(seq_len - 1, -1, -1):
        slot = anchor - timedelta(hours=offset)
        key = slot.strftime("%Y-%m-%dT%H:%M:%S")
        row = by_hour.get(key)
        if row:
            glucose = float(row.get("blood_glucose") or glucose)
            insulin = float(row.get("insulin_total") or insulin)
            meal = float(row.get("meal_flag") or meal)
            exercise = float(row.get("exercise_flag") or exercise)
        series.append(
            {
                "observed_at": key,
                "blood_glucose": glucose,
                "insulin_total": insulin,
                "meal_flag": meal,
                "exercise_flag": exercise,
            }
        )
    return series


class GlucosePredictor:
    def __init__(self, model_path: Path):
        import onnxruntime as ort

        self.session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

    def predict(self, observations: list[dict]) -> tuple[list[float], float] | None:
        hourly = resample_hourly(observations) if len(observations) != SEQ_LEN else observations
        mat = build_feature_matrix(hourly)
        if mat is None:
            return None
        inp = mat.reshape(1, SEQ_LEN, N_FEATURES)
        out = self.session.run(None, {self.input_name: inp})[0]
        values = denormalize_glucose([float(v) for v in out[0].tolist()[:HORIZON]])
        confidence = max(0.5, min(0.95, 1.0 - np.std(values) / 10.0))
        return values, confidence
