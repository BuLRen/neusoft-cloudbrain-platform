"""Fine-tune LSTM + GRU on UCI patients (demo / fingerstick alignment)."""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, FEATURE_NAMES, GRU_PT_NAME, PT_NAME  # noqa: E402
from src.dataset import GlucoseSequenceDataset, build_windows  # noqa: E402
from src.model_gru import GlucoseGRU  # noqa: E402
from src.model_lstm import GlucoseLSTM  # noqa: E402
from src.norm import normalize_targets, normalize_windows  # noqa: E402
from src.parsers.uci import load_uci_series  # noqa: E402
from src.training import train_model  # noqa: E402


def _uci_windows() -> tuple[np.ndarray, np.ndarray]:
    xs, ys = [], []
    for _, hourly in load_uci_series():
        x, y = build_windows(hourly[FEATURE_NAMES].to_numpy(dtype=float))
        if len(x):
            xs.append(x)
            ys.append(y)
    if not xs:
        raise SystemExit("No UCI windows for fine-tune")
    x_all = normalize_windows(np.concatenate(xs, axis=0))
    y_all = normalize_targets(np.concatenate(ys, axis=0))
    return x_all, y_all


def _finetune_one(model: nn.Module, name: str, save_name: str, epochs: int = 25) -> float:
    x_all, y_all = _uci_windows()
    split = int(len(x_all) * 0.85)
    train_loader = DataLoader(GlucoseSequenceDataset(x_all[:split], y_all[:split]), batch_size=64, shuffle=True)
    val_loader = DataLoader(GlucoseSequenceDataset(x_all[split:], y_all[split:]), batch_size=128)
    model.load_state_dict(torch.load(ARTIFACTS / save_name, map_location="cpu"))
    return train_model(
        model,
        train_loader,
        val_loader,
        ARTIFACTS / save_name,
        epochs=epochs,
        lr=2e-4,
        patience=8,
        loss_fn=nn.HuberLoss(delta=1.0),
        on_epoch=lambda e, tr, va: print(f"{name} finetune epoch={e:02d} train={tr:.4f} val={va:.4f}"),
    )


def main() -> None:
    lstm_best = _finetune_one(GlucoseLSTM(), "lstm", PT_NAME)
    gru_best = _finetune_one(GlucoseGRU(), "gru", GRU_PT_NAME)
    print(f"UCI fine-tune LSTM best: {lstm_best:.4f}, GRU best: {gru_best:.4f}")


if __name__ == "__main__":
    main()
