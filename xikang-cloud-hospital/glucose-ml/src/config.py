from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_RAW = ROOT / "data" / "raw"
DATA_PROCESSED = ROOT / "data" / "processed"
ARTIFACTS = ROOT / "artifacts"
REPORTS = ROOT / "reports"

SEQ_LEN = 48
HORIZON = 24
N_FEATURES = 4

FEATURE_NAMES = ["blood_glucose", "insulin_total", "meal_flag", "exercise_flag"]

GLUCOSE_CODES = {48, 57, 58, 59, 60, 61, 62, 63, 64}
INSULIN_CODES = {33, 34, 35}
MEAL_CODES = {66, 67, 68}
EXERCISE_CODES = {69, 70, 71}

MODEL_ID = "glucose_lstm_v1"
ONNX_NAME = f"{MODEL_ID}.onnx"
PT_NAME = f"{MODEL_ID}.pt"
