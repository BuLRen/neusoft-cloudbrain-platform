package com.xikang.schedule.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医生请假申请表
 * leave_request: 记录医生请假申请
 */
@Data
public class LeaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long physicianId;        // 医生ID
    private LocalDate leaveDate;    // 请假日期
    private String timeSlot;       // 请假时段：上午/下午/全天
    private String leaveType;      // 请假类型：事假/病假/公假/其他
    private String reason;         // 请假原因
    private String rawText;       // 原始请假文本（供AI解析）
    private LocalDate aiParsedDate; // AI解析后的日期
    private String aiParsedSlot;   // AI解析后的时段
    private BigDecimal aiConfidence; // AI解析置信度
    private String status;         // 状态：待审批/已批准/已拒绝/已处理
    private Long approverId;       // 审批人
    private LocalDateTime approvalTime; // 审批时间
    private Boolean autoProcessed;  // 是否已自动处理
    private LocalDateTime createTime; // 创建时间
    private Integer delmark;        // 删除标记：0=有效/1=删除

    // 扩展字段（非数据库字段）
    private String physicianName; // 医生姓名
    private String approverName;   // 审批人姓名
}