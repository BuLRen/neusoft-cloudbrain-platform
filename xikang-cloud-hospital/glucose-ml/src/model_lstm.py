import torch
from torch import nn

from src.config import HIDDEN_SIZE, HORIZON, N_FEATURES


class GlucoseLSTM(nn.Module):
    def __init__(self, input_size: int = N_FEATURES, hidden_size: int = HIDDEN_SIZE, horizon: int = HORIZON):
        super().__init__()
        self.lstm = nn.LSTM(input_size, hidden_size, num_layers=2, batch_first=True, dropout=0.2)
        self.head = nn.Sequential(
            nn.Linear(hidden_size, hidden_size),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_size, horizon),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out, _ = self.lstm(x)
        last = out[:, -1, :]
        return self.head(last)
