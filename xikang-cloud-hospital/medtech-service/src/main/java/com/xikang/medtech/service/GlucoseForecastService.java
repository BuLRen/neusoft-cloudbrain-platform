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
    private static final double HYPO_MMOL = 3.9;
    private static final double HYPER_MMOL = 10.0;

    private final GlucoseForecastMapper glucoseForecastMapper;
    private final GlucosePredictionClient glucosePredictionClient;
    private final HealthObservationService healthObservationService;

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

    public Map<String, Object> buildAdvice(Long registerId) {
        if (!isGlucosePatient(registerId)) {
            throw new BusinessException("该患者不属于血糖监测队列");
        }

        Map<String, Object> advice = new LinkedHashMap<>();
        advice.put("registerId", registerId);

        int recentReports = healthObservationService.countRecentGlucoseReports(registerId, 48);
        advice.put("recentReportCount", recentReports);

        if (recentReports < 2) {
            advice.put("riskLevel", "unknown");
            advice.put("revisitRecommended", false);
            advice.put("adviceText", "请继续录入居家血糖（建议 48 小时内至少 2 次），以便生成可靠预测与复诊建议。");
            return advice;
        }

        Map<String, Object> forecast;
        try {
            forecast = getForecast(registerId, null, null);
        } catch (BusinessException ex) {
            advice.put("riskLevel", "unknown");
            advice.put("revisitRecommended", false);
            advice.put("adviceText", ex.getMessage());
            return advice;
        }

        String riskLevel = forecast.get("riskLevel") != null
            ? String.valueOf(forecast.get("riskLevel"))
            : "unknown";
        advice.put("riskLevel", riskLevel);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) forecast.get("forecasts");
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        if (points != null) {
            for (Map<String, Object> point : points) {
                double value = toDouble(point.get("forecastValue"));
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }

        boolean revisitRecommended = "high".equalsIgnoreCase(riskLevel)
            || (points != null && !points.isEmpty() && (min < HYPO_MMOL || max > HYPER_MMOL));
        advice.put("revisitRecommended", revisitRecommended);
        if (points != null && !points.isEmpty()) {
            advice.put("forecastMin", min);
            advice.put("forecastMax", max);
        }
        if (forecast.get("modelId") != null) {
            advice.put("modelId", forecast.get("modelId"));
        }
        if (forecast.get("confidence") != null) {
            advice.put("confidence", forecast.get("confidence"));
        }

        if (revisitRecommended) {
            if (min < HYPO_MMOL) {
                advice.put("adviceText", "预测血糖可能偏低（<3.9 mmol/L），建议尽快到院复诊，请通过患者端「我的挂号」自行预约。");
            } else if (max > HYPER_MMOL) {
                advice.put("adviceText", "预测血糖可能偏高（>10 mmol/L），建议尽快到院复诊，请通过患者端「我的挂号」自行预约。");
            } else {
                advice.put("adviceText", "模型评估为高风险，建议尽快到院复诊，请通过患者端「我的挂号」自行预约。");
            }
        } else if ("medium".equalsIgnoreCase(riskLevel)) {
            advice.put("adviceText", "血糖波动需关注，请保持规律监测；如持续异常请到「我的挂号」自行预约复诊。");
        } else {
            advice.put("adviceText", "当前预测风险较低，请继续按时录入血糖并遵循随访计划。");
        }

        return advice;
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

    public void refreshForecastAsync(Long registerId) {
        try {
            refreshForecast(registerId);
        } catch (Exception ex) {
            log.warn("异步刷新血糖预测失败 registerId={}: {}", registerId, ex.getMessage());
        }
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
