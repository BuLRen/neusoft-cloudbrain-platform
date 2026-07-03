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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlucoseForecastService {

    private static final String METRIC_CODE = "blood_glucose";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final double HYPO_MMOL = 3.9;
    private static final double HYPER_MMOL = 10.0;
    private static final double GLUCOSE_MIN_MMOL = 2.2;
    private static final double GLUCOSE_MAX_MMOL = 22.0;
    private static final int FORECAST_HOURS = 24;
    private static final int MIN_FORECAST_POINTS = 8;
    private static final int ML_SEQ_LEN = 48;
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

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
        pruneStaleForecasts(registerId);
        LocalDateTime lastActual = healthObservationService.getLatestPatientGlucoseObservationAt(registerId);
        List<Map<String, Object>> forecasts = filterFutureForecasts(
            glucoseForecastMapper.selectForecasts(registerId, METRIC_CODE, from, to),
            lastActual
        );
        if (forecasts.size() < MIN_FORECAST_POINTS || isForecastStale(registerId, lastActual)) {
            return refreshForecast(registerId);
        }
        return buildForecastResult(registerId, forecasts);
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
            advice.put("adviceText", "请继续录入居家血糖（建议 48 小时内至少 2 次），以便生成更准确的预测与健康建议。");
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
                advice.put("adviceText", "近期血糖波动较大，建议尽快到院复诊，请通过患者端「我的挂号」自行预约。");
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

        LocalDateTime lastActual = healthObservationService.getLatestPatientGlucoseObservationAt(registerId);
        Double latestPatientGlucose = healthObservationService.getLatestPatientGlucoseValue(registerId);
        pruneStaleForecasts(registerId);

        List<Map<String, Object>> allRows = glucoseForecastMapper.selectObservationsForPrediction(registerId);
        List<Map<String, Object>> patientRows = glucoseForecastMapper.selectPatientObservationsForPrediction(registerId);
        LocalDateTime anchor = resolvePredictionAnchor(lastActual, allRows, patientRows);
        List<Map<String, Object>> mlRows = buildMlHourlySeries(allRows, patientRows, anchor);
        List<Map<String, Object>> payload = buildPredictionPayload(mlRows);

        Map<String, Object> prediction = glucosePredictionClient.predict(registerId, payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("metricCode", METRIC_CODE);
        result.put("observationCount", payload.size());
        result.put("mlInputHours", mlRows.size());
        result.put("prediction", prediction);

        Object message = prediction.get("message");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forecastPoints = prediction.containsKey("forecast")
            ? (List<Map<String, Object>>) prediction.get("forecast")
            : List.of();

        String modelId = String.valueOf(prediction.getOrDefault("model_id", "glucose_lstm_v1"));
        String riskLevel = String.valueOf(prediction.getOrDefault("risk_level", "unknown"));
        double confidence = toDouble(prediction.get("confidence"));

        if (forecastPoints.isEmpty()) {
            double baseValue = latestPatientGlucose != null
                ? latestPatientGlucose
                : resolveLatestGlucoseValue(allRows);
            forecastPoints = synthesizeForecastPoints(anchor, baseValue, FORECAST_HOURS);
            modelId = "glucose_demo_curve";
            riskLevel = classifyDemoRisk(forecastPoints);
            confidence = 0.55;
            String hint = message != null ? String.valueOf(message) : "模型推理暂不可用";
            result.put("message", hint + "；已回退为基于末次实测的简化预测");
        }

        persistForecastPoints(registerId, forecastPoints, lastActual, modelId, riskLevel, confidence);

        result.put("riskLevel", riskLevel);
        result.put("modelId", modelId);
        result.put("confidence", confidence);
        result.put("forecasts", filterFutureForecasts(
            glucoseForecastMapper.selectForecasts(registerId, METRIC_CODE, null, null),
            lastActual
        ));
        return result;
    }

    public void refreshForecastAsync(Long registerId) {
        try {
            refreshForecast(registerId);
        } catch (Exception ex) {
            log.warn("异步刷新血糖预测失败 registerId={}: {}", registerId, ex.getMessage());
        }
    }

    private Map<String, Object> buildForecastResult(Long registerId, List<Map<String, Object>> forecasts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registerId", registerId);
        result.put("metricCode", METRIC_CODE);
        result.put("forecasts", forecasts);
        Map<String, Object> meta = glucoseForecastMapper.selectLatestForecastMeta(registerId, METRIC_CODE);
        if (meta != null) {
            result.putAll(meta);
        }
        return result;
    }

    private void persistForecastPoints(
        Long registerId,
        List<Map<String, Object>> forecastPoints,
        LocalDateTime lastActual,
        String modelId,
        String riskLevel,
        double confidence
    ) {
        glucoseForecastMapper.deleteForecasts(registerId, METRIC_CODE);
        for (Map<String, Object> point : forecastPoints) {
            LocalDateTime at = LocalDateTime.parse(String.valueOf(point.get("forecast_at")), ISO);
            if (lastActual != null && !at.isAfter(lastActual)) {
                continue;
            }
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
    }

    private List<Map<String, Object>> synthesizeForecastPoints(LocalDateTime anchor, double baseValue, int hours) {
        List<Map<String, Object>> points = new ArrayList<>();
        double value = clampGlucose(baseValue);
        for (int i = 1; i <= hours; i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("forecast_at", anchor.plusHours(i).format(ISO));
            point.put("value", Math.round(value * 10.0) / 10.0);
            points.add(point);
        }
        return points;
    }

    private double clampGlucose(double value) {
        return Math.max(GLUCOSE_MIN_MMOL, Math.min(GLUCOSE_MAX_MMOL, value));
    }

    private List<Map<String, Object>> buildMlHourlySeries(
        List<Map<String, Object>> allRows,
        List<Map<String, Object>> patientRows,
        LocalDateTime anchor
    ) {
        Map<Long, Map<String, Object>> mergedByHour = new LinkedHashMap<>();
        for (Map<String, Object> row : allRows) {
            LocalDateTime at = parseObservationAt(row.get("observedAt"));
            if (at != null) {
                mergedByHour.put(hourKey(at), row);
            }
        }
        for (Map<String, Object> row : patientRows) {
            LocalDateTime at = parseObservationAt(row.get("observedAt"));
            if (at != null) {
                mergedByHour.put(hourKey(at), row);
            }
        }

        LocalDateTime anchorHour = anchor.truncatedTo(ChronoUnit.HOURS);
        double glucose = latestPatientGlucoseFallback(patientRows, allRows);
        for (int i = ML_SEQ_LEN - 1; i >= 0; i--) {
            LocalDateTime slot = anchorHour.minusHours(i);
            Map<String, Object> seed = mergedByHour.get(hourKey(slot));
            if (seed != null && seed.get("bloodGlucose") != null) {
                glucose = toDouble(seed.get("bloodGlucose"));
                break;
            }
        }
        double insulin = 0.0;
        double meal = 0.0;
        double exercise = 0.0;

        List<Map<String, Object>> series = new ArrayList<>(ML_SEQ_LEN);
        for (int i = ML_SEQ_LEN - 1; i >= 0; i--) {
            LocalDateTime slot = anchorHour.minusHours(i);
            Map<String, Object> row = mergedByHour.get(hourKey(slot));
            if (row != null) {
                if (row.get("bloodGlucose") != null) {
                    glucose = toDouble(row.get("bloodGlucose"));
                }
                if (row.get("insulinTotal") != null) {
                    insulin = toDouble(row.get("insulinTotal"));
                }
                if (row.get("mealFlag") != null) {
                    meal = toDouble(row.get("mealFlag"));
                }
                if (row.get("exerciseFlag") != null) {
                    exercise = toDouble(row.get("exerciseFlag"));
                }
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("observedAt", slot.format(ISO));
            point.put("bloodGlucose", glucose);
            point.put("insulinTotal", insulin);
            point.put("mealFlag", meal);
            point.put("exerciseFlag", exercise);
            series.add(point);
        }
        return series;
    }

    private LocalDateTime resolvePredictionAnchor(
        LocalDateTime lastPatientActual,
        List<Map<String, Object>> allRows,
        List<Map<String, Object>> patientRows
    ) {
        if (lastPatientActual != null) {
            return lastPatientActual;
        }
        LocalDateTime latest = null;
        for (Map<String, Object> row : allRows) {
            LocalDateTime at = parseObservationAt(row.get("observedAt"));
            if (at != null && (latest == null || at.isAfter(latest))) {
                latest = at;
            }
        }
        for (Map<String, Object> row : patientRows) {
            LocalDateTime at = parseObservationAt(row.get("observedAt"));
            if (at != null && (latest == null || at.isAfter(latest))) {
                latest = at;
            }
        }
        return latest != null ? latest : LocalDateTime.now();
    }

    private double latestPatientGlucoseFallback(
        List<Map<String, Object>> patientRows,
        List<Map<String, Object>> allRows
    ) {
        if (!patientRows.isEmpty()) {
            return toDouble(patientRows.get(patientRows.size() - 1).get("bloodGlucose"));
        }
        if (!allRows.isEmpty()) {
            return toDouble(allRows.get(allRows.size() - 1).get("bloodGlucose"));
        }
        return 7.5;
    }

    private long hourKey(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.HOURS).atZone(SYSTEM_ZONE).toEpochSecond();
    }

    private LocalDateTime parseObservationAt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T').substring(0, Math.min(19, text.length())), ISO);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Map<String, Object>> buildPredictionPayload(List<Map<String, Object>> rows) {
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
        return payload;
    }

    private boolean isForecastStale(Long registerId, LocalDateTime lastActual) {
        if (lastActual == null) {
            return false;
        }
        Map<String, Object> meta = glucoseForecastMapper.selectLatestForecastMeta(registerId, METRIC_CODE);
        if (meta == null || meta.get("creationTime") == null) {
            return true;
        }
        LocalDateTime created = parseDateTime(meta.get("creationTime"));
        return created == null || !created.isAfter(lastActual);
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T').substring(0, Math.min(19, text.length())), ISO);
        } catch (Exception ex) {
            return null;
        }
    }

    private double resolveLatestGlucoseValue(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 7.5;
        }
        return toDouble(rows.get(rows.size() - 1).get("bloodGlucose"));
    }

    private String classifyDemoRisk(List<Map<String, Object>> forecastPoints) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Map<String, Object> point : forecastPoints) {
            double value = toDouble(point.get("value"));
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (min < HYPO_MMOL || max > HYPER_MMOL) {
            return "high";
        }
        if (max > 7.8) {
            return "medium";
        }
        return "low";
    }

    private void pruneStaleForecasts(Long registerId) {
        LocalDateTime lastActual = healthObservationService.getLatestPatientGlucoseObservationAt(registerId);
        if (lastActual == null) {
            return;
        }
        glucoseForecastMapper.deleteForecastsBeforeOrEqual(registerId, METRIC_CODE, lastActual);
    }

    private List<Map<String, Object>> filterFutureForecasts(
        List<Map<String, Object>> forecasts,
        LocalDateTime lastActual
    ) {
        if (forecasts == null || forecasts.isEmpty() || lastActual == null) {
            return forecasts != null ? forecasts : List.of();
        }
        return forecasts.stream()
            .filter(point -> {
                LocalDateTime at = parseForecastAt(point.get("forecastAt"));
                return at != null && at.isAfter(lastActual);
            })
            .collect(Collectors.toList());
    }

    private LocalDateTime parseForecastAt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T').substring(0, Math.min(19, text.length())), ISO);
        } catch (Exception ex) {
            return null;
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
