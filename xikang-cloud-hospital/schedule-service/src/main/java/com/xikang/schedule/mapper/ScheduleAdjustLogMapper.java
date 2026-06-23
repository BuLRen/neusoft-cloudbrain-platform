package com.xikang.schedule.mapper;

import com.xikang.schedule.entity.ScheduleAdjustLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduleAdjustLogMapper {

    List<ScheduleAdjustLog> selectAll();

    ScheduleAdjustLog selectById(@Param("id") Long id);

    /**
     * 查询某排班的调整日志
     */
    List<ScheduleAdjustLog> selectByScheduleId(@Param("scheduleId") Long scheduleId);

    /**
     * 查询某调整人的调整日志
     */
    List<ScheduleAdjustLog> selectByAdjustBy(@Param("adjustBy") Long adjustBy);

    int insert(ScheduleAdjustLog log);

    int deleteById(@Param("id") Long id);
}