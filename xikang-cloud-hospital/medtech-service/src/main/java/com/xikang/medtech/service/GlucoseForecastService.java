package com.xikang.medtech.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.medtech.client.GlucosePredictionClient;
import com.xikang.medtech.mapper.GlucoseForecastMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlucoseForecastService {

    private static final String METRIC_CODE = "blood_glucose";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GlucoseForecastMapper glucoseForecastMapper;
    private final GlucosePredictionClient glucosePredictionClient;

    public boolean isGlucosePatient(Long registerId) {
        return registerId != null && glucoseForecastMapper.isGlucoseCohortPatient(registerId);
    }

    public Map<String, Object> getForecast(Long registerId, LocalDate from, LocalDate to) {
        if (!isGlucosePatient(registerId)) {
            throw new BusinessException("该患者不属于血糖监测队列");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("metricCode", METRIC_CODE);
        result.put("forecasts", glucoseForecastMapper.selectForecasts(registerId, METRIC_CODE, from, to));
        Map<String, Object> meta = glucoseForecastMapper.selectLatestForecastMeta(registerId, METRIC_CODE);
        if (meta != null) {
            result.putAll(meta);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> refreshForecast(Long registerId) {
        if (!isGlucosePatient(registerId)) {
            throw new BusinessException("该患者不属于血糖监测队列");
        }

        List<Map<String, Object>> rows = glucoseForecastMapper.selectObservationsForPrediction(registerId);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("observed_at", row.get("observedAt"));
            item.put("blood_glucose", toDouble(row.get("bloodGlucose")));
            item.put("insulin_total", toDouble(row.get("insulinTotal")));
            item.put("meal_flag", toDouble(row.get("mealFlag")));
            item.put("exercise_flag", toDouble(row.get("exerciseFlag")));
            payload.add(item);
        }

        Map<String, Object> prediction = glucosePredictionClient.predict(registerId, payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("metricCode", METRIC_CODE);
        result.put("observationCount", payload.size());
        result.put("prediction", prediction);

        Object message = prediction.get("message");
        if (message != null && !prediction.containsKey("forecast")) {
            result.put("message", String.valueOf(message));
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forecastPoints = (List<Map<String, Object>>) prediction.get("forecast");
        if (forecastPoints == null || forecastPoints.isEmpty()) {
            result.put("message", prediction.getOrDefault("message", "预测数据不足"));
            return result;
        }

        String modelId = String.valueOf(prediction.getOrDefault("model_id", "glucose_lstm_v1"));
        String riskLevel = String.valueOf(prediction.getOrDefault("risk_level", "unknown"));
        double confidence = toDouble(prediction.get("confidence"));

        glucoseForecastMapper.deleteForecasts(registerId, METRIC_CODE);
        for (Map<String, Object> point : forecastPoints) {
            LocalDateTime at = LocalDateTime.parse(String.valueOf(point.get("forecast_at")), ISO);
            Map<String, Object> row = new HashMap<>();
            row.put("registerId", registerId);
            row.put("metricCode", METRIC_CODE);
            row.put("forecastAt", at);
            row.put("forecastValue", toDouble(point.get("value")));
            row.put("confidence", confidence);
            row.put("modelId", modelId);
            row.put("riskLevel", riskLevel);
            glucoseForecastMapper.insertForecast(row);
        }

        result.put("riskLevel", riskLevel);
        result.put("modelId", modelId);
        result.put("confidence", confidence);
        result.put("forecasts", glucoseForecastMapper.selectForecasts(registerId, METRIC_CODE, null, null));
        return result;
    }

    private static double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
