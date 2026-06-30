from pydantic import BaseModel, Field


class ObservationPoint(BaseModel):
    observed_at: str
    blood_glucose: float | None = None
    insulin_total: float = 0.0
    meal_flag: float = 0.0
    exercise_flag: float = 0.0


class PredictRequest(BaseModel):
    register_id: int
    observations: list[ObservationPoint] = Field(default_factory=list)


class ForecastPoint(BaseModel):
    forecast_at: str
    value: float


class PredictResponse(BaseModel):
    register_id: int
    model_id: str
    risk_level: str
    confidence: float
    forecast: list[ForecastPoint]
    message: str | None = None
