from __future__ import annotations

from collections.abc import Callable
from pathlib import Path

import torch
from torch import nn
from torch.utils.data import DataLoader


def _ensure_save_parent(save_path) -> Path:
    path = Path(save_path)
    parent = path.parent
    parent.mkdir(parents=True, exist_ok=True)
    return path


def train_model(
    model: nn.Module,
    train_loader: DataLoader,
    val_loader: DataLoader,
    save_path,
    *,
    epochs: int = 100,
    lr: float = 1e-3,
    patience: int = 12,
    loss_fn: nn.Module | None = None,
    on_epoch: Callable[[int, float, float], None] | None = None,
) -> float:
    device = torch.device("cpu")
    model = model.to(device)
    opt = torch.optim.Adam(model.parameters(), lr=lr)
    criterion = loss_fn or nn.HuberLoss(delta=1.0)
    best_val = float("inf")
    bad = 0

    for epoch in range(1, epochs + 1):
        model.train()
        train_loss = 0.0
        for xb, yb in train_loader:
            xb, yb = xb.to(device), yb.to(device)
            opt.zero_grad()
            pred = model(xb)
            loss = criterion(pred, yb)
            loss.backward()
            opt.step()
            train_loss += loss.item() * len(xb)
        train_loss /= len(train_loader.dataset)

        model.eval()
        val_loss = 0.0
        with torch.no_grad():
            for xb, yb in val_loader:
                xb, yb = xb.to(device), yb.to(device)
                val_loss += criterion(model(xb), yb).item() * len(xb)
        val_loss /= len(val_loader.dataset)

        if on_epoch:
            on_epoch(epoch, train_loss, val_loss)
        else:
            print(f"epoch={epoch:03d} train={train_loss:.4f} val={val_loss:.4f}")

        if val_loss < best_val:
            best_val = val_loss
            bad = 0
            resolved = _ensure_save_parent(save_path)
            torch.save(model.state_dict(), resolved)
        else:
            bad += 1
            if bad >= patience:
                print("Early stopping.")
                break

    return best_val
