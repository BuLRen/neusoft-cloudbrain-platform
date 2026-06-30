from __future__ import annotations

from pathlib import Path

import numpy as np
import torch
from torch.utils.data import Dataset

from src.config import HORIZON, SEQ_LEN


class GlucoseSequenceDataset(Dataset):
    def __init__(self, windows: np.ndarray, targets: np.ndarray):
        self.windows = windows.astype(np.float32)
        self.targets = targets.astype(np.float32)

    def __len__(self) -> int:
        return len(self.windows)

    def __getitem__(self, idx: int):
        return torch.from_numpy(self.windows[idx]), torch.from_numpy(self.targets[idx])


def build_windows(features: np.ndarray, seq_len: int = SEQ_LEN, horizon: int = HORIZON):
    """features: (T, F) with column 0 = blood_glucose."""
    xs, ys = [], []
    for start in range(0, len(features) - seq_len - horizon + 1):
        x = features[start : start + seq_len]
        y = features[start + seq_len : start + seq_len + horizon, 0]
        if np.isnan(y).any() or np.isnan(x).any():
            continue
        xs.append(x)
        ys.append(y)
    if not xs:
        return np.empty((0, seq_len, features.shape[1])), np.empty((0, horizon))
    return np.stack(xs), np.stack(ys)


def load_processed() -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    proc = Path(__file__).resolve().parents[1] / "data" / "processed"
    return (
        np.load(proc / "train_x.npy"),
        np.load(proc / "train_y.npy"),
        np.load(proc / "val_x.npy"),
        np.load(proc / "val_y.npy"),
    )
