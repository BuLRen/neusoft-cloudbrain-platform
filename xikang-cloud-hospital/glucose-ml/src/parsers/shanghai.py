from __future__ import annotations

from pathlib import Path

import pandas as pd

from src.config import DATA_RAW, MIN_HOURLY_POINTS
from src.hourly import mg_dl_to_mmol, resample_to_hourly

SHANGHAI_COHORTS = ("Shanghai_T1DM", "Shanghai_T2DM")


def _find_cohort_dirs(root: Path) -> list[tuple[str, Path]]:
    found: list[tuple[str, Path]] = []
    for cohort in SHANGHAI_COHORTS:
        for candidate in root.rglob(cohort):
            if candidate.is_dir():
                found.append((cohort, candidate))
                break
    return found


def _parse_sheet(path: Path) -> pd.DataFrame:
    try:
        df = pd.read_excel(path)
    except Exception:
        return pd.DataFrame()
    if df.empty:
        return pd.DataFrame()

    date_col = next((c for c in df.columns if str(c).lower().startswith("date")), df.columns[0])
    glucose_col = next((c for c in df.columns if "cgm" in str(c).lower()), None)
    if glucose_col is None:
        return pd.DataFrame()

    out = pd.DataFrame({"ts": pd.to_datetime(df[date_col], errors="coerce")})
    glucose = pd.to_numeric(df[glucose_col], errors="coerce")
    out["blood_glucose"] = glucose.apply(mg_dl_to_mmol) if glucose.median(skipna=True) > 30 else glucose

    insulin_cols = [
        c
        for c in df.columns
        if "insulin" in str(c).lower() and "agent" not in str(c).lower()
    ]
    if insulin_cols:
        insulin = pd.DataFrame({c: pd.to_numeric(df[c], errors="coerce") for c in insulin_cols})
        out["insulin_total"] = insulin.fillna(0.0).sum(axis=1)
    else:
        out["insulin_total"] = 0.0

    meal_cols = [c for c in df.columns if "dietary" in str(c).lower() or str(c) == "饮食"]
    if meal_cols:
        meal_text = df[meal_cols[0]].astype(str).str.lower()
        out["meal_flag"] = (
            (~meal_text.isin({"nan", "未记录", "data not available", ""}))
            & meal_text.notna()
        ).astype(float)
    else:
        out["meal_flag"] = 0.0

    out["exercise_flag"] = 0.0
    return out.dropna(subset=["ts", "blood_glucose"]).sort_values("ts")


def load_shanghai_series() -> list[tuple[str, pd.DataFrame]]:
    root = DATA_RAW / "shanghai"
    cohort_dirs = _find_cohort_dirs(root)
    if not cohort_dirs:
        return []

    series: list[tuple[str, pd.DataFrame]] = []
    for cohort, cohort_dir in cohort_dirs:
        prefix = "T1" if "T1DM" in cohort else "T2"
        for path in sorted(cohort_dir.glob("*")):
            if path.suffix.lower() not in {".xlsx", ".xls"} or path.name.startswith("~$"):
                continue
            if "Summary" in path.name:
                continue
            events = _parse_sheet(path)
            if events.empty:
                continue
            hourly = resample_to_hourly(events)
            if len(hourly) >= MIN_HOURLY_POINTS:
                series.append((f"sh_{prefix}_{path.stem}", hourly))
    return series
