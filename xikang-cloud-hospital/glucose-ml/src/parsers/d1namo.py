from __future__ import annotations

from pathlib import Path

import pandas as pd

from src.config import DATA_RAW, MIN_HOURLY_POINTS
from src.hourly import mg_dl_to_mmol, resample_to_hourly

D1NAMO_GLUCOSE_DIRS = (
    "diabetes_subset_pictures-glucose-food-insulin",
    "healthy_subset_pictures-glucose-food",
)


def _glucose_csv_paths() -> list[tuple[str, Path]]:
    found: list[tuple[str, Path]] = []
    for dir_name in D1NAMO_GLUCOSE_DIRS:
        subset = "dm" if "diabetes" in dir_name else "healthy"
        for base in (DATA_RAW / dir_name, DATA_RAW / dir_name / dir_name):
            if not base.exists():
                continue
            for path in sorted(base.rglob("glucose.csv")):
                patient_id = path.parent.name
                if not patient_id.isdigit():
                    continue
                key = f"d1_{subset}_{patient_id}"
                found.append((key, path))
    return found


def _read_glucose_csv(path: Path) -> pd.DataFrame:
    try:
        df = pd.read_csv(path)
    except Exception:
        return pd.DataFrame()
    if df.empty:
        return pd.DataFrame()

    work = df.copy()
    if "type" in work.columns:
        types = work["type"].astype(str).str.lower()
        cgm_mask = types.eq("cgm")
        if cgm_mask.any():
            work = work.loc[cgm_mask]
        else:
            work = work.loc[types.str.contains("glucose|finger|capillary|cgm", regex=True, na=False)]
        if work.empty:
            work = df.copy()

    if {"date", "time"}.issubset(work.columns):
        ts = pd.to_datetime(
            work["date"].astype(str).str.strip() + " " + work["time"].astype(str).str.strip(),
            errors="coerce",
        )
    else:
        lower = {str(c).lower(): c for c in work.columns}
        ts_col = next(
            (
                lower[k]
                for k in lower
                if k in {"timestamp", "datetime", "date_time", "time"} or "time" in k
            ),
            work.columns[0],
        )
        ts = pd.to_datetime(work[ts_col], errors="coerce")

    lower = {str(c).lower(): c for c in work.columns}
    val_col = next(
        (lower[k] for k in lower if k == "glucose" or "glucose" in k or k in {"value", "bg", "cgm"}),
        None,
    )
    if val_col is None:
        return pd.DataFrame()

    glucose = pd.to_numeric(work[val_col], errors="coerce")
    if glucose.median(skipna=True) > 30:
        glucose = glucose.apply(mg_dl_to_mmol)
    elif glucose.median(skipna=True) > 5:
        glucose = glucose * 70.0 / 3.88 / 18.0

    out = pd.DataFrame({"ts": ts, "blood_glucose": glucose}).dropna()
    if out.empty:
        return out

    out["insulin_total"] = 0.0
    out["meal_flag"] = 0.0
    out["exercise_flag"] = 0.0
    return out.sort_values("ts")


def _merge_insulin(events: pd.DataFrame, glucose_path: Path) -> pd.DataFrame:
    insulin_path = glucose_path.parent / "insulin.csv"
    if not insulin_path.exists() or events.empty:
        return events

    try:
        ins = pd.read_csv(insulin_path)
    except Exception:
        return events
    if ins.empty:
        return events

    if {"date", "time"}.issubset(ins.columns):
        ins_ts = pd.to_datetime(
            ins["date"].astype(str).str.strip() + " " + ins["time"].astype(str).str.strip(),
            errors="coerce",
        )
    else:
        lower = {str(c).lower(): c for c in ins.columns}
        ts_col = next((lower[k] for k in lower if "time" in k or "date" in k), ins.columns[0])
        ins_ts = pd.to_datetime(ins[ts_col], errors="coerce")

    lower = {str(c).lower(): c for c in ins.columns}
    dose_col = next((lower[k] for k in lower if "insulin" in k or k in {"value", "dose", "units"}), None)
    if dose_col is None:
        return events

    doses = pd.to_numeric(ins[dose_col], errors="coerce")
    insulin_events = pd.DataFrame({"ts": ins_ts, "insulin_total": doses}).dropna()
    if insulin_events.empty:
        return events

    merged = pd.merge(events, insulin_events, on="ts", how="outer")
    merged["blood_glucose"] = merged["blood_glucose"].ffill().bfill()
    for col in ["insulin_total", "meal_flag", "exercise_flag"]:
        if col not in merged.columns:
            merged[col] = 0.0
        merged[col] = merged[col].fillna(0.0)
    return merged.dropna(subset=["blood_glucose"]).sort_values("ts")


def load_d1namo_series() -> list[tuple[str, pd.DataFrame]]:
    series: list[tuple[str, pd.DataFrame]] = []
    for patient_key, glucose_path in _glucose_csv_paths():
        events = _merge_insulin(_read_glucose_csv(glucose_path), glucose_path)
        if events.empty:
            continue
        hourly = resample_to_hourly(events)
        if len(hourly) >= MIN_HOURLY_POINTS:
            series.append((patient_key, hourly))
    return series
