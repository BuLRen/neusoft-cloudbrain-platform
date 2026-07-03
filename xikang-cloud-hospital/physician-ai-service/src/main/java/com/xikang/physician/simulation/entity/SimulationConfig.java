package com.xikang.physician.simulation.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SimulationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String configKey;
    private String techCode;
    private String checkName;
    private String matchKeywords;
    private Boolean enabled;
    private String simulationMode;
    private Integer version;
    private String promptSections;
    private String diseaseMappings;
    private String outputSchema;
    private String defaults;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
