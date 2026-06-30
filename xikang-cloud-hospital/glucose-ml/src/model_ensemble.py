import torch
from torch import nn

from src.config import ENSEMBLE_GRU_WEIGHT, ENSEMBLE_LSTM_WEIGHT
from src.model_gru import GlucoseGRU
from src.model_lstm import GlucoseLSTM


class GlucoseEnsemble(nn.Module):
    """LSTM + GRU weighted average for export and evaluation."""

    def __init__(
        self,
        lstm: GlucoseLSTM | None = None,
        gru: GlucoseGRU | None = None,
        w_lstm: float = ENSEMBLE_LSTM_WEIGHT,
        w_gru: float = ENSEMBLE_GRU_WEIGHT,
    ):
        super().__init__()
        self.lstm = lstm or GlucoseLSTM()
        self.gru = gru or GlucoseGRU()
        self.w_lstm = w_lstm
        self.w_gru = w_gru

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.w_lstm * self.lstm(x) + self.w_gru * self.gru(x)
