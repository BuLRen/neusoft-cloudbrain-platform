"""Evaluate LSTM+GRU ensemble: overall + per-source + UCI demo subset."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import torch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, FEATURE_NAMES, GRU_PT_NAME, MODEL_ID, PT_NAME, REPORTS  # noqa: E402
from src.dataset import GlucoseSequenceDataset, build_windows, load_processed  # noqa: E402
from src.metrics_clarke import clarke_ab_percent, plot_clarke_grid  # noqa: E402
from src.model_ensemble import GlucoseEnsemble  # noqa: E402
from src.norm import denormalize_targets, load_norm_stats, normalize_targets, normalize_windows  # noqa: E402
from src.parsers import (  # noqa: E402
    load_d1namo_series,
    load_shanghai_series,
    load_uci_series,
    load_uchtt1dm_series,
)

SOURCE_LOADERS = {
    "uci": load_uci_series,
    "uchtt1dm": load_uchtt1dm_series,
    "shanghai": load_shanghai_series,
    "d1namo": load_d1namo_series,
}


def mard(actual: np.ndarray, predicted: np.ndarray) -> float:
    mask = actual > 0
    if not mask.any():
        return 0.0
    return float(np.mean(np.abs(actual[mask] - predicted[mask]) / actual[mask]) * 100)


def _metrics_block(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float | int]:
    mae = float(np.mean(np.abs(predicted - actual)))
    rmse = float(np.sqrt(np.mean((predicted - actual) ** 2)))
    return {
        "mae_mmol_l": round(mae, 4),
        "rmse_mmol_l": round(rmse, 4),
        "mard_percent": round(mard(actual, predicted), 2),
        "clarke_ab_percent": round(clarke_ab_percent(actual, predicted), 2),
        "val_samples": int(len(actual)),
    }


def load_ensemble() -> GlucoseEnsemble:
    model = GlucoseEnsemble()
    model.lstm.load_state_dict(torch.load(ARTIFACTS / PT_NAME, map_location="cpu"))
    model.gru.load_state_dict(torch.load(ARTIFACTS / GRU_PT_NAME, map_location="cpu"))
    model.eval()
    return model


def predict_batch(model: GlucoseEnsemble, x: np.ndarray) -> np.ndarray:
    with torch.no_grad():
        return model(torch.from_numpy(x.astype(np.float32))).numpy()


def eval_concat(model: GlucoseEnsemble, val_x: np.ndarray, val_y: np.ndarray, stats: dict) -> tuple[np.ndarray, np.ndarray]:
    from torch.utils.data import DataLoader

    from src.dataset import GlucoseSequenceDataset

    loader = DataLoader(GlucoseSequenceDataset(val_x, val_y), batch_size=256)
    preds, actuals = [], []
    with torch.no_grad():
        for xb, yb in loader:
            preds.append(model(xb).numpy())
            actuals.append(yb.numpy())
    pred_all = denormalize_targets(np.concatenate(preds, axis=0).reshape(-1), stats)
    act_all = denormalize_targets(np.concatenate(actuals, axis=0).reshape(-1), stats)
    return pred_all, act_all


def eval_by_loaders(model: GlucoseEnsemble, stats: dict) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for source, loader in SOURCE_LOADERS.items():
        preds, actuals = [], []
        for _, hourly in loader():
            x, y = build_windows(hourly[FEATURE_NAMES].to_numpy(dtype=float))
            if len(x) == 0:
                continue
            x = normalize_windows(x, stats)
            y = normalize_targets(y, stats)
            pred = predict_batch(model, x)
            preds.append(pred.reshape(-1))
            actuals.append(y.reshape(-1))
        if not preds:
            out[source] = {"val_samples": 0}
            continue
        pred_all = denormalize_targets(np.concatenate(preds), stats)
        act_all = denormalize_targets(np.concatenate(actuals), stats)
        out[source] = _metrics_block(act_all, pred_all)
    return out


def main() -> None:
    _, _, val_x, val_y = load_processed()
    stats = load_norm_stats()
    model = load_ensemble()

    pred_all, act_all = eval_concat(model, val_x, val_y, stats)
    by_source = eval_by_loaders(model, stats)

    stats_path = ROOT / "data" / "processed" / "dataset_stats.json"
    datasets = {}
    if stats_path.exists():
        datasets = json.loads(stats_path.read_text(encoding="utf-8")).get("sources", {})

    metrics = {
        "model_id": MODEL_ID,
        "model_type": "lstm_gru_ensemble",
        **_metrics_block(act_all, pred_all),
        "datasets": datasets,
        "by_source": by_source,
        "demo_uci": by_source.get("uci", {}),
    }

    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    plot_clarke_grid(act_all, pred_all, REPORTS / "clarke_grid.png")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
