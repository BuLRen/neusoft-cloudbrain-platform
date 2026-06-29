"""Parse UCI diabetes files → hourly multivariate series → train/val windows."""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import (  # noqa: E402
    DATA_PROCESSED,
    DATA_RAW,
    EXERCISE_CODES,
    FEATURE_NAMES,
    GLUCOSE_CODES,
    INSULIN_CODES,
    MEAL_CODES,
    SEQ_LEN,
    HORIZON,
)
from src.dataset import build_windows  # noqa: E402


def mg_dl_to_mmol(v: float) -> float:
    return v / 18.0


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


def to_hourly(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return pd.DataFrame(columns=["ts"] + FEATURE_NAMES)

    df = df.sort_values("ts")
    start = df["ts"].min().floor("h")
    end = df["ts"].max().ceil("h")
    idx = pd.date_range(start, end, freq="h")

    glucose = []
    insulin = []
    meal = []
    exercise = []

    for ts in idx:
        window = df[(df["ts"] >= ts) & (df["ts"] < ts + pd.Timedelta(hours=1))]
        g_vals = window.loc[window["code"].isin(GLUCOSE_CODES), "value"]
        glucose.append(mg_dl_to_mmol(g_vals.mean()) if len(g_vals) else np.nan)
        i_vals = window.loc[window["code"].isin(INSULIN_CODES), "value"]
        insulin.append(float(i_vals.sum()) if len(i_vals) else 0.0)
        meal.append(1.0 if window["code"].isin(MEAL_CODES).any() else 0.0)
        exercise.append(1.0 if window["code"].isin(EXERCISE_CODES).any() else 0.0)

    out = pd.DataFrame(
        {
            "ts": idx,
            "blood_glucose": glucose,
            "insulin_total": insulin,
            "meal_flag": meal,
            "exercise_flag": exercise,
        }
    )
    out["blood_glucose"] = out["blood_glucose"].interpolate().ffill().bfill()
    return out


def find_patient_files() -> list[Path]:
    files = list(DATA_RAW.rglob("data-*"))
    files = [p for p in files if p.is_file() and p.stat().st_size > 100]
    return sorted(set(files))


def main() -> None:
    DATA_PROCESSED.mkdir(parents=True, exist_ok=True)
    files = find_patient_files()
    if not files:
        raise SystemExit(f"No patient files under {DATA_RAW}. Run scripts/download_uci.py first.")

    all_x, all_y = [], []
    for path in files:
        hourly = to_hourly(parse_patient_file(path))
        if len(hourly) < SEQ_LEN + HORIZON + 10:
            continue
        feats = hourly[FEATURE_NAMES].to_numpy(dtype=float)
        x, y = build_windows(feats)
        if len(x):
            all_x.append(x)
            all_y.append(y)

    if not all_x:
        raise SystemExit("No training windows built.")

    X = np.concatenate(all_x, axis=0)
    Y = np.concatenate(all_y, axis=0)
    n = len(X)
    split = int(n * 0.8)
    rng = np.random.default_rng(42)
    perm = rng.permutation(n)
    train_idx, val_idx = perm[:split], perm[split:]

    np.save(DATA_PROCESSED / "train_x.npy", X[train_idx])
    np.save(DATA_PROCESSED / "train_y.npy", Y[train_idx])
    np.save(DATA_PROCESSED / "val_x.npy", X[val_idx])
    np.save(DATA_PROCESSED / "val_y.npy", Y[val_idx])
    print(f"Windows: total={n}, train={len(train_idx)}, val={len(val_idx)}")


if __name__ == "__main__":
    main()
