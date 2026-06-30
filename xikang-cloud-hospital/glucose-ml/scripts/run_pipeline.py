"""End-to-end: download -> prepare -> train -> evaluate -> export ONNX -> copy to inference service."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
INFERENCE_MODEL = ROOT.parent / "glucose-prediction-service" / "models" / "glucose_lstm_v1.onnx"


def run(cmd: list[str]) -> None:
    print("+", " ".join(cmd))
    subprocess.run(cmd, cwd=ROOT, check=True)


def main() -> None:
    skip_d1namo = "--skip-d1namo" in sys.argv
    download_cmd = [sys.executable, str(SCRIPTS / "download_datasets.py")]
    if skip_d1namo:
        download_cmd.append("--skip-d1namo")

    run(download_cmd)
    run([sys.executable, str(SCRIPTS / "prepare_all.py")])
    run([sys.executable, str(SCRIPTS / "train_lstm.py")])
    run([sys.executable, str(SCRIPTS / "finetune_uci.py")])
    run([sys.executable, str(SCRIPTS / "evaluate.py")])
    run([sys.executable, str(SCRIPTS / "export_onnx.py")])

    src = ROOT / "artifacts" / "glucose_lstm_v1.onnx"
    INFERENCE_MODEL.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, INFERENCE_MODEL)
    norm_src = ROOT / "data" / "processed" / "norm_stats.json"
    norm_dst = INFERENCE_MODEL.parent / "norm_stats.json"
    if norm_src.exists():
        shutil.copy2(norm_src, norm_dst)
    print(f"copied model to {INFERENCE_MODEL}")


if __name__ == "__main__":
    main()
