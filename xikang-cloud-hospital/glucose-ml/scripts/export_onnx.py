"""Export trained LSTM to ONNX."""

from __future__ import annotations

import sys
from pathlib import Path

import torch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, ONNX_NAME, PT_NAME, SEQ_LEN, N_FEATURES  # noqa: E402
from src.model_lstm import GlucoseLSTM  # noqa: E402


def main() -> None:
    model = GlucoseLSTM()
    model.load_state_dict(torch.load(ARTIFACTS / PT_NAME, map_location="cpu"))
    model.eval()

    dummy = torch.randn(1, SEQ_LEN, N_FEATURES)
    out_path = ARTIFACTS / ONNX_NAME
    torch.onnx.export(
        model,
        dummy,
        out_path,
        input_names=["input"],
        output_names=["forecast"],
        dynamic_axes={"input": {0: "batch"}, "forecast": {0: "batch"}},
        opset_version=17,
    )
    print(f"Exported {out_path}")


if __name__ == "__main__":
    main()
