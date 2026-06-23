package com.xikang.pharmacy.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 药房统计聚合 Mapper。
 * 所有方法返回 Map，便于 Service 灵活组装报表字段。
 */
@Mapper
public interface StatisticsMapper {

    /** 顶部 KPI：发药单数 / 发放数量 / 发放金额 / 入库数量 / 退药金额 */
    Map<String, Object> selectOverview(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /** 发药量 TOP 药品（按数量降序，limit 条）*/
    List<Map<String, Object>> selectTopDrugs(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate,
                                             @Param("limit") int limit);

    /** 药师工作量 */
    List<Map<String, Object>> selectOperatorStats(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
}
