package com.xikang.payment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Register Info Mapper（v3.2：payment-service 只读 register + department + employee）
 *
 * 用于 buildOrder 时填充订单的 departmentName / doctorName / visitDate。
 * 本服务不写 register 表，只做跨表 JOIN 读。
 */
@Mapper
public interface RegisterInfoMapper {

    /**
     * 批量取挂号基础信息（按 registerId 列表）。
     * 返回字段：registerId, departmentName, doctorName, visitDate
     */
    List<Map<String, Object>> selectBasicByIds(@Param("ids") List<Long> ids);
}
