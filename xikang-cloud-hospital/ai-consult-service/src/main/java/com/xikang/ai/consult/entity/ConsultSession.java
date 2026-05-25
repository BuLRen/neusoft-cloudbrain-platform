package com.xikang.ai.consult.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Consult Session Entity
 */
@Data
public class ConsultSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String sessionId;
    private Long patientId;
    private Integer status;
    private String summary;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
