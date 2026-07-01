package com.xikang.physician.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Dify W3 workflow inputs (string JSON fields per workflow start-node contract).
 */
@Component
public class W3DifyInputBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> build(
        Long registerId,
        Map<String, Object> structuredRecord,
        List<Map<String, Object>> allResults,
        String preliminaryAssessment
    ) {
        try {
            String structuredRecordJson = MAPPER.writeValueAsString(
                structuredRecord == null ? Map.of() : structuredRecord
            );
            String allResultsJson = MAPPER.writeValueAsString(
                allResults == null ? List.of() : allResults
            );
            String assessment = preliminaryAssessment == null ? "" : preliminaryAssessment;

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("registerId", String.valueOf(registerId));
            // Dify 开始节点常见命名：structuredRecord / allResults（字符串 JSON）
            inputs.put("structuredRecord", structuredRecordJson);
            inputs.put("allResults", allResultsJson);
            // 兼容 Json 后缀命名的工作流版本
            inputs.put("structuredRecordJson", structuredRecordJson);
            inputs.put("allResultsJson", allResultsJson);
            inputs.put("preliminaryAssessment", assessment);
            return inputs;
        } catch (Exception ex) {
            throw new IllegalStateException("W3 Dify 输入序列化失败", ex);
        }
    }
}
