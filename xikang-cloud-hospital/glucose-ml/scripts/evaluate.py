"""Evaluate model: MAE, RMSE, MARD, Clarke A+B."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import torch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, MODEL_ID, PT_NAME, REPORTS  # noqa: E402
from src.dataset import GlucoseSequenceDataset, load_processed  # noqa: E402
from src.metrics_clarke import clarke_ab_percent, plot_clarke_grid  # noqa: E402
from src.model_lstm import GlucoseLSTM  # noqa: E402


def mard(actual: np.ndarray, predicted: np.ndarray) -> float:
    mask = actual > 0
    if not mask.any():
        return 0.0
    return float(np.mean(np.abs(actual[mask] - predicted[mask]) / actual[mask]) * 100)


def main() -> None:
    _, _, val_x, val_y = load_processed()
    model = GlucoseLSTM()
    model.load_state_dict(torch.load(ARTIFACTS / PT_NAME, map_location="cpu"))
    model.eval()

    loader = torch.utils.data.DataLoader(GlucoseSequenceDataset(val_x, val_y), batch_size=256)
    preds, actuals = [], []
    with torch.no_grad():
        for xb, yb in loader:
            pred = model(xb)
            preds.append(pred.numpy())
            actuals.append(yb.numpy())

    pred_all = np.concatenate(preds, axis=0).reshape(-1)
    act_all = np.concatenate(actuals, axis=0).reshape(-1)

    mae = float(np.mean(np.abs(pred_all - act_all)))
    rmse = float(np.sqrt(np.mean((pred_all - act_all) ** 2)))
    mard_val = mard(act_all, pred_all)
    clarke_ab = clarke_ab_percent(act_all, pred_all)

    REPORTS.mkdir(parents=True, exist_ok=True)
    metrics = {
        "model_id": MODEL_ID,
        "mae_mmol_l": round(mae, 4),
        "rmse_mmol_l": round(rmse, 4),
        "mard_percent": round(mard_val, 2),
        "clarke_ab_percent": round(clarke_ab, 2),
        "val_samples": int(len(pred_all)),
    }
    (REPORTS / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    plot_clarke_grid(act_all, pred_all, REPORTS / "clarke_grid.png")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
