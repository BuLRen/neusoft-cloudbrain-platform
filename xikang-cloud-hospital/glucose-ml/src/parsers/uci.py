from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from src.config import (
    DATA_RAW,
    EXERCISE_CODES,
    GLUCOSE_CODES,
    INSULIN_CODES,
    MEAL_CODES,
    MIN_HOURLY_POINTS,
)
from src.hourly import mg_dl_to_mmol, resample_to_hourly


def parse_patient_file(path: Path) -> pd.DataFrame:
    rows = []
    for line in path.read_text(encoding="latin-1").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        date_str, time_str, code_str, val_str = parts[0], parts[1], parts[2], parts[3]
        try:
            code = int(code_str)
            val = float(val_str)
        except ValueError:
            continue
        ts = pd.to_datetime(f"{date_str} {time_str}", format="%m-%d-%Y %H:%M", errors="coerce")
        if pd.isna(ts):
            continue
        rows.append({"ts": ts, "code": code, "value": val})
    return pd.DataFrame(rows)


def events_from_uci(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return pd.DataFrame(columns=["ts", "blood_glucose", "insulin_total", "meal_flag", "exercise_flag"])

    events = []
    for ts, group in df.groupby("ts"):
        g_vals = group.loc[group["code"].isin(GLUCOSE_CODES), "value"]
        if not len(g_vals):
            continue
        i_vals = group.loc[group["code"].isin(INSULIN_CODES), "value"]
        events.append(
            {
                "ts": ts,
                "blood_glucose": mg_dl_to_mmol(float(g_vals.mean())),
                "insulin_total": float(i_vals.sum()) if len(i_vals) else 0.0,
                "meal_flag": 1.0 if group["code"].isin(MEAL_CODES).any() else 0.0,
                "exercise_flag": 1.0 if group["code"].isin(EXERCISE_CODES).any() else 0.0,
            }
        )
    return pd.DataFrame(events)


def find_patient_files(root: Path | None = None) -> list[Path]:
    root = root or DATA_RAW
    files = [p for p in root.rglob("data-*") if p.is_file() and p.stat().st_size > 100]
    return sorted(set(files))


def load_uci_series() -> list[tuple[str, pd.DataFrame]]:
    series: list[tuple[str, pd.DataFrame]] = []
    for path in find_patient_files():
        patient_id = f"uci_{path.name}"
        hourly = resample_to_hourly(events_from_uci(parse_patient_file(path)))
        if len(hourly) >= MIN_HOURLY_POINTS:
            series.append((patient_id, hourly))
    return series
