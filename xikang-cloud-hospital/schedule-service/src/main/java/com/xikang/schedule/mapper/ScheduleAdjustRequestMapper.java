package com.xikang.schedule.mapper;

import com.xikang.schedule.entity.ScheduleAdjustRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduleAdjustRequestMapper {

    List<ScheduleAdjustRequest> selectAll();

    ScheduleAdjustRequest selectById(@Param("id") Long id);

    /**
     * 查询待确认的调整申请
     */
    List<ScheduleAdjustRequest> selectPending();

    /**
     * 查询某排班的调整申请
     */
    List<ScheduleAdjustRequest> selectByScheduleId(@Param("scheduleId") Long scheduleId);

    /**
     * 查询某触发人的调整申请
     */
    List<ScheduleAdjustRequest> selectByTriggeredBy(@Param("triggeredBy") Long triggeredBy);

    int insert(ScheduleAdjustRequest request);

    int updateStatus(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("confirmedBy") Long confirmedBy,
                    @Param("confirmTime") java.time.LocalDateTime confirmTime,
                    @Param("confirmRemark") String confirmRemark);

    int deleteById(@Param("id") Long id);
}