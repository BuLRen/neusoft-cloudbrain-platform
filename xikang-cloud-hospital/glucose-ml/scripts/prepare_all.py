"""Build train/val windows from all glucose datasets (patient-level split)."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import DATA_PROCESSED, FEATURE_NAMES, HORIZON, SEQ_LEN  # noqa: E402
from src.dataset import build_windows  # noqa: E402
from src.parsers import (  # noqa: E402
    load_d1namo_series,
    load_shanghai_series,
    load_uci_series,
    load_uchtt1dm_series,
)


def main() -> None:
    loaders = [
        ("uci", load_uci_series),
        ("uchtt1dm", load_uchtt1dm_series),
        ("shanghai", load_shanghai_series),
        ("d1namo", load_d1namo_series),
    ]

    patient_windows: list[tuple[str, np.ndarray, np.ndarray]] = []
    stats: dict[str, object] = {"sources": {}, "patients": []}

    for source_name, loader in loaders:
        series = loader()
        source_count = 0
        for patient_id, hourly in series:
            feats = hourly[FEATURE_NAMES].to_numpy(dtype=float)
            x, y = build_windows(feats)
            if len(x) == 0:
                continue
            patient_windows.append((patient_id, x, y))
            source_count += 1
            stats["patients"].append({"id": patient_id, "source": source_name, "hours": len(hourly), "windows": len(x)})
        stats["sources"][source_name] = source_count
        print(f"{source_name}: {source_count} patients")

    if not patient_windows:
        raise SystemExit("No training windows built. Run scripts/download_datasets.py first.")

    rng = np.random.default_rng(42)
    ids = [p[0] for p in patient_windows]
    perm = rng.permutation(len(ids))
    split_at = max(1, int(len(ids) * 0.8))
    train_ids = {ids[i] for i in perm[:split_at]}
    val_ids = {ids[i] for i in perm[split_at:]}
    if not val_ids:
        val_ids = {ids[perm[-1]]}
        train_ids = {i for i in ids if i not in val_ids}

    train_x = np.concatenate([x for pid, x, _ in patient_windows if pid in train_ids], axis=0)
    train_y = np.concatenate([y for pid, _, y in patient_windows if pid in train_ids], axis=0)
    val_x = np.concatenate([x for pid, x, _ in patient_windows if pid in val_ids], axis=0)
    val_y = np.concatenate([y for pid, _, y in patient_windows if pid in val_ids], axis=0)

    DATA_PROCESSED.mkdir(parents=True, exist_ok=True)
    np.save(DATA_PROCESSED / "train_x.npy", train_x)
    np.save(DATA_PROCESSED / "train_y.npy", train_y)
    np.save(DATA_PROCESSED / "val_x.npy", val_x)
    np.save(DATA_PROCESSED / "val_y.npy", val_y)

    g_mean = float(train_x[:, :, 0].mean())
    g_std = float(max(train_x[:, :, 0].std(), 1e-6))
    train_x = train_x.copy()
    val_x = val_x.copy()
    train_x[:, :, 0] = (train_x[:, :, 0] - g_mean) / g_std
    val_x[:, :, 0] = (val_x[:, :, 0] - g_mean) / g_std
    train_y = (train_y - g_mean) / g_std
    val_y = (val_y - g_mean) / g_std
    np.save(DATA_PROCESSED / "train_x.npy", train_x)
    np.save(DATA_PROCESSED / "train_y.npy", train_y)
    np.save(DATA_PROCESSED / "val_x.npy", val_x)
    np.save(DATA_PROCESSED / "val_y.npy", val_y)
    norm_stats = {"glucose_mean": g_mean, "glucose_std": g_std}
    (DATA_PROCESSED / "norm_stats.json").write_text(json.dumps(norm_stats, indent=2), encoding="utf-8")

    stats["windows"] = {"total": len(train_x) + len(val_x), "train": len(train_x), "val": len(val_x)}
    stats["split"] = {"train_patients": len(train_ids), "val_patients": len(val_ids)}
    (DATA_PROCESSED / "dataset_stats.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
    print(json.dumps(stats["sources"], indent=2))
    print(f"windows train={len(train_x)} val={len(val_x)}")


if __name__ == "__main__":
    main()
