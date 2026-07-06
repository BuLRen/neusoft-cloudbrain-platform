package com.xikang.physician.simulation.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SimulationConfigSaveRequest {

    private String configKey;
    private String techCode;
    private String checkName;
    private String matchKeywords;
    private Boolean enabled;
    private String simulationMode;
    private Map<String, String> promptSections;
    private List<Map<String, Object>> diseaseMappings;
    private Map<String, Object> outputSchema;
    private Map<String, String> defaults;
}
