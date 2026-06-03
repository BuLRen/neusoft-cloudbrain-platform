package com.xikang.registration.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * Scheduling Entity - 排班规则表
 * scheduling: id, rule_name, week_rule, delmark
 * 注意：此表仅存储排班规则，不存储具体号源信息
 */
@Data
public class Scheduling implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String ruleName;     // rule_name (规则名称，如"周一至周五上午")
    private String weekRule;     // week_rule (星期规则，格式: 1上2上3上4上5上)
    private Integer delmark;     // delmark: 0=有效/1=删除
}