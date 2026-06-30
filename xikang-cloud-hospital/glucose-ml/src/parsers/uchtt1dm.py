from __future__ import annotations

from pathlib import Path

import pandas as pd

from src.config import DATA_RAW, MIN_HOURLY_POINTS
from src.hourly import mg_dl_to_mmol, resample_to_hourly


def _read_signal_xlsx(path: Path) -> pd.DataFrame:
    df = pd.read_excel(path)
    if df.empty:
        return pd.DataFrame(columns=["ts", "value"])
    cols = {c.lower(): c for c in df.columns}
    time_col = next((cols[k] for k in cols if "time" in k or "date" in k or "fecha" in k), df.columns[0])
    val_col = next((cols[k] for k in cols if "glucose" in k or "value" in k or "valor" in k), df.columns[-1])
    out = pd.DataFrame(
        {
            "ts": pd.to_datetime(df[time_col], errors="coerce"),
            "value": pd.to_numeric(df[val_col], errors="coerce"),
        }
    )
    return out.dropna(subset=["ts", "value"])


def _patient_events(folder: Path) -> pd.DataFrame:
    glucose_path = folder / "Glucose.xlsx"
    if not glucose_path.exists():
        return pd.DataFrame()

    glucose = _read_signal_xlsx(glucose_path)
    glucose["blood_glucose"] = glucose["value"].apply(mg_dl_to_mmol)

    insulin_total = 0.0
    insulin_path = folder / "Insulin.xlsx"
    if insulin_path.exists():
        ins = _read_signal_xlsx(insulin_path)
        insulin_events = ins.rename(columns={"value": "insulin_total"})
    else:
        insulin_events = pd.DataFrame(columns=["ts", "insulin_total"])

    carb_path = folder / "Carbohidrates.xlsx"
    if carb_path.exists():
        carb = _read_signal_xlsx(carb_path)
        carb_events = carb.rename(columns={"value": "carb_g"})
    else:
        carb_events = pd.DataFrame(columns=["ts", "carb_g"])

    steps_path = folder / "Steps.xlsx"
    if steps_path.exists():
        steps = _read_signal_xlsx(steps_path)
        step_events = steps.rename(columns={"value": "steps"})
    else:
        step_events = pd.DataFrame(columns=["ts", "steps"])

    frames = [glucose[["ts", "blood_glucose"]]]
    if not insulin_events.empty:
        frames.append(insulin_events[["ts", "insulin_total"]])
    if not carb_events.empty:
        carb_events["meal_flag"] = (carb_events["carb_g"] > 0).astype(float)
        frames.append(carb_events[["ts", "meal_flag"]])
    if not step_events.empty:
        step_events["exercise_flag"] = (step_events["steps"] > 0).astype(float)
        frames.append(step_events[["ts", "exercise_flag"]])

    merged = frames[0]
    for frame in frames[1:]:
        merged = pd.merge(merged, frame, on="ts", how="outer")

    for col in ["insulin_total", "meal_flag", "exercise_flag"]:
        if col not in merged.columns:
            merged[col] = 0.0
        merged[col] = merged[col].fillna(0.0)
    merged = merged.dropna(subset=["blood_glucose"])
    return merged.sort_values("ts")


def load_uchtt1dm_series() -> list[tuple[str, pd.DataFrame]]:
    root = DATA_RAW / "UC_HT_T1DM"
    if not root.exists():
        return []

    series: list[tuple[str, pd.DataFrame]] = []
    for folder in sorted(root.iterdir()):
        if not folder.is_dir():
            continue
        if not (folder / "Glucose.xlsx").exists():
            continue
        hourly = resample_to_hourly(_patient_events(folder))
        if len(hourly) >= MIN_HOURLY_POINTS:
            series.append((f"uch_{folder.name}", hourly))
    return series
