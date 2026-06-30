from __future__ import annotations

from datetime import datetime, timedelta
from pathlib import Path

from fastapi import FastAPI, HTTPException

from app.config import HORIZON, MODEL_ID, PORT
from app.predictor import GlucosePredictor, classify_risk
from app.schemas import ForecastPoint, PredictRequest, PredictResponse

MODEL_PATH = Path(__file__).resolve().parents[1] / "models" / "glucose_lstm_v1.onnx"
predictor: GlucosePredictor | None = None

app = FastAPI(title="Glucose Prediction Service", version="1.0.0")


@app.on_event("startup")
def load_model() -> None:
    global predictor
    if not MODEL_PATH.exists():
        raise RuntimeError(f"Model not found: {MODEL_PATH}")
    predictor = GlucosePredictor(MODEL_PATH)


@app.get("/health")
def health():
    return {"status": "UP", "model_id": MODEL_ID, "model_loaded": predictor is not None}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    if predictor is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    obs = sorted(req.observations, key=lambda o: o.observed_at)
    dicts = [o.model_dump() for o in obs]
    result = predictor.predict(dicts)
    if result is None:
        return PredictResponse(
            register_id=req.register_id,
            model_id=MODEL_ID,
            risk_level="unknown",
            confidence=0.0,
            forecast=[],
            message=f"Need at least 48 hourly observations, got {len(obs)}",
        )

    values, confidence = result
    base_time = datetime.fromisoformat(obs[-1].observed_at.replace("Z", "+00:00").replace(" ", "T")[:19])
    points = []
    for i, val in enumerate(values[:HORIZON]):
        ts = base_time + timedelta(hours=i + 1)
        points.append(ForecastPoint(forecast_at=ts.strftime("%Y-%m-%dT%H:%M:%S"), value=round(val, 3)))

    risk = classify_risk([p.value for p in points])
    return PredictResponse(
        register_id=req.register_id,
        model_id=MODEL_ID,
        risk_level=risk,
        confidence=round(confidence, 4),
        forecast=points,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=PORT, reload=False)
