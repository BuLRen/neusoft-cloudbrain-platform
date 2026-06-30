"""Train LSTM + GRU glucose forecasters (ensemble components)."""

from __future__ import annotations

import sys
from pathlib import Path

import torch
from torch import nn
from torch.utils.data import DataLoader

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, GRU_PT_NAME, PT_NAME  # noqa: E402
from src.dataset import GlucoseSequenceDataset, load_processed  # noqa: E402
from src.model_gru import GlucoseGRU  # noqa: E402
from src.model_lstm import GlucoseLSTM  # noqa: E402
from src.training import train_model  # noqa: E402


def main() -> None:
    train_x, train_y, val_x, val_y = load_processed()
    train_loader = DataLoader(GlucoseSequenceDataset(train_x, train_y), batch_size=64, shuffle=True)
    val_loader = DataLoader(GlucoseSequenceDataset(val_x, val_y), batch_size=128)
    loss_fn = nn.HuberLoss(delta=1.0)
    ARTIFACTS.mkdir(parents=True, exist_ok=True)

    print("=== training LSTM ===")
    lstm = GlucoseLSTM()
    lstm_best = train_model(
        lstm,
        train_loader,
        val_loader,
        ARTIFACTS / PT_NAME,
        loss_fn=loss_fn,
        on_epoch=lambda e, tr, va: print(f"lstm epoch={e:03d} train={tr:.4f} val={va:.4f}"),
    )
    print(f"LSTM best val loss: {lstm_best:.4f}")

    print("=== training GRU ===")
    gru = GlucoseGRU()
    gru_best = train_model(
        gru,
        train_loader,
        val_loader,
        ARTIFACTS / GRU_PT_NAME,
        loss_fn=loss_fn,
        on_epoch=lambda e, tr, va: print(f"gru  epoch={e:03d} train={tr:.4f} val={va:.4f}"),
    )
    print(f"GRU best val loss: {gru_best:.4f}")


if __name__ == "__main__":
    main()
