"""Resample irregular CGM events to hourly feature matrix."""

from __future__ import annotations

import numpy as np
import pandas as pd

from src.config import FEATURE_NAMES


def mg_dl_to_mmol(value: float) -> float:
    return value / 18.0


def resample_to_hourly(events: pd.DataFrame) -> pd.DataFrame:
    """events columns: ts, blood_glucose (mmol/L), insulin_total, meal_flag, exercise_flag."""
    if events.empty:
        return pd.DataFrame(columns=["ts", *FEATURE_NAMES])

    df = events.sort_values("ts").copy()
    start = df["ts"].min().floor("h")
    end = df["ts"].max().ceil("h")
    idx = pd.date_range(start, end, freq="h")

    rows = []
    for ts in idx:
        window = df[(df["ts"] >= ts) & (df["ts"] < ts + pd.Timedelta(hours=1))]
        glucose = window["blood_glucose"].dropna()
        insulin = window["insulin_total"].dropna() if "insulin_total" in window else pd.Series(dtype=float)
        meal = window["meal_flag"].dropna() if "meal_flag" in window else pd.Series(dtype=float)
        exercise = window["exercise_flag"].dropna() if "exercise_flag" in window else pd.Series(dtype=float)

        rows.append(
            {
                "ts": ts,
                "blood_glucose": float(glucose.mean()) if len(glucose) else np.nan,
                "insulin_total": float(insulin.sum()) if len(insulin) else 0.0,
                "meal_flag": 1.0 if (meal > 0).any() else 0.0,
                "exercise_flag": 1.0 if (exercise > 0).any() else 0.0,
            }
        )

    out = pd.DataFrame(rows)
    out["blood_glucose"] = out["blood_glucose"].interpolate().ffill().bfill()
    return out
