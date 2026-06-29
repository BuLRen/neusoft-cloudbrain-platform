"""Train LSTM glucose forecaster."""

from __future__ import annotations

import sys
from pathlib import Path

import torch
from torch import nn
from torch.utils.data import DataLoader

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, PT_NAME  # noqa: E402
from src.dataset import GlucoseSequenceDataset, load_processed  # noqa: E402
from src.model_lstm import GlucoseLSTM  # noqa: E402


def main() -> None:
    train_x, train_y, val_x, val_y = load_processed()
    train_loader = DataLoader(GlucoseSequenceDataset(train_x, train_y), batch_size=64, shuffle=True)
    val_loader = DataLoader(GlucoseSequenceDataset(val_x, val_y), batch_size=128)

    device = torch.device("cpu")
    model = GlucoseLSTM().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=1e-3)
    loss_fn = nn.MSELoss()

    best_val = float("inf")
    patience, bad = 12, 0
    ARTIFACTS.mkdir(parents=True, exist_ok=True)

    for epoch in range(1, 101):
        model.train()
        train_loss = 0.0
        for xb, yb in train_loader:
            xb, yb = xb.to(device), yb.to(device)
            opt.zero_grad()
            pred = model(xb)
            loss = loss_fn(pred, yb)
            loss.backward()
            opt.step()
            train_loss += loss.item() * len(xb)
        train_loss /= len(train_loader.dataset)

        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for xb, yb in val_loader:
                xb, yb = xb.to(device), yb.to(device)
                pred = model(xb)
                val_loss += loss_fn(pred, yb).item() * len(xb)
        val_loss /= len(val_loader.dataset)

        print(f"epoch={epoch:03d} train_mse={train_loss:.4f} val_mse={val_loss:.4f}")
        if val_loss < best_val:
            best_val = val_loss
            bad = 0
            torch.save(model.state_dict(), ARTIFACTS / PT_NAME)
        else:
            bad += 1
            if bad >= patience:
                print("Early stopping.")
                break

    print(f"Best val MSE: {best_val:.4f}, saved {ARTIFACTS / PT_NAME}")


if __name__ == "__main__":
    main()
