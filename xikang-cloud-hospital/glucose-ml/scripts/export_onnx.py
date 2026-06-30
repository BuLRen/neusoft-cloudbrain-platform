"""Export LSTM+GRU ensemble to ONNX (single file for inference service)."""

from __future__ import annotations

import sys
from pathlib import Path

import torch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from src.config import ARTIFACTS, GRU_PT_NAME, ONNX_NAME, PT_NAME, SEQ_LEN, N_FEATURES  # noqa: E402
from src.model_ensemble import GlucoseEnsemble  # noqa: E402


def main() -> None:
    model = GlucoseEnsemble()
    model.lstm.load_state_dict(torch.load(ARTIFACTS / PT_NAME, map_location="cpu"))
    model.gru.load_state_dict(torch.load(ARTIFACTS / GRU_PT_NAME, map_location="cpu"))
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
    print(f"Exported ensemble {out_path}")


if __name__ == "__main__":
    main()
