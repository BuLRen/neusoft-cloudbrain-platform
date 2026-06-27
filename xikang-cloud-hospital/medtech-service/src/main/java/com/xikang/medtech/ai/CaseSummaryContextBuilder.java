package com.xikang.medtech.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.medtech.mapper.FollowUpCommunicationMapper;
import com.xikang.medtech.mapper.FollowUpOutcomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CaseSummaryContextBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FollowUpOutcomeMapper followUpOutcomeMapper;
    private final FollowUpCommunicationMapper followUpCommunicationMapper;

    public Map<String, Object> build(Long registerId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(30);

        Map<String, Object> profile = followUpOutcomeMapper.selectPatientProfile(registerId);
        Map<String, Object> detail = followUpOutcomeMapper.selectPatientDetail(registerId);
        List<Map<String, Object>> diseases = followUpOutcomeMapper.selectPatientDiseases(registerId);
        List<Map<String, Object>> metrics = followUpCommunicationMapper.selectRecentMetrics(registerId, from, today);
        List<Map<String, Object>> records = followUpCommunicationMapper.selectRecentFollowUpRecords(registerId, 5);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("registerId", registerId);
        inputs.put("patientName", textOrEmpty(profile, "realName"));
        inputs.put("caseNumber", textOrEmpty(profile, "caseNumber"));
        inputs.put("gender", textOrEmpty(profile, "gender"));
        inputs.put("age", profile != null ? profile.get("age") : null);
        inputs.put("diagnosis", textOrEmpty(detail, "diagnosis"));
        inputs.put("chiefComplaint", textOrEmpty(detail, "chiefComplaint"));
        inputs.put("presentIllness", textOrEmpty(detail, "presentIllness"));
        inputs.put("allergy", textOrEmpty(detail, "allergy"));
        inputs.put("diseasesJson", toJson(diseases));
        inputs.put("metricsJson", toJson(summarizeMetrics(metrics)));
        inputs.put("followUpRecordsJson", toJson(records));

        Map<String, Object> observation = followUpCommunicationMapper.selectTodayObservation(registerId, today);
        Map<String, Object> interview = followUpCommunicationMapper.selectTodayInterview(registerId, today);
        inputs.put("observedToday", observation != null && !observation.isEmpty());
        inputs.put("interviewScheduledToday", interview != null && !interview.isEmpty());

        return inputs;
    }

    public Map<String, Object> buildMedicalChatInputs(Long registerId, String patientMessage, List<Map<String, Object>> recentMessages) {
        Map<String, Object> base = build(registerId);
        base.put("patientMessage", patientMessage == null ? "" : patientMessage.trim());
        base.put("recentMessagesJson", toJson(recentMessages == null ? List.of() : recentMessages));
        return base;
    }

    private List<Map<String, Object>> summarizeMetrics(List<Map<String, Object>> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }
        Map<String, List<Map<String, Object>>> grouped = metrics.stream()
            .collect(Collectors.groupingBy(m -> String.valueOf(m.get("metricKey"))));
        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> list = entry.getValue();
            Map<String, Object> latest = list.get(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("metricKey", entry.getKey());
            row.put("latestValue", latest.get("metricValue"));
            row.put("unit", latest.get("unit"));
            row.put("latestDate", latest.get("recordDate"));
            if (list.size() > 1) {
                row.put("previousValue", list.get(list.size() - 1).get("metricValue"));
            }
            summary.add(row);
        }
        return summary;
    }

    private String textOrEmpty(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return "";
        }
        return String.valueOf(map.get(key)).trim();
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
