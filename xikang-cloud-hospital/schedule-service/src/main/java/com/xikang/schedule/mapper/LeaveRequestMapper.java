package com.xikang.schedule.mapper;

import com.xikang.schedule.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LeaveRequestMapper {

    List<LeaveRequest> selectAll();

    LeaveRequest selectById(@Param("id") Long id);

    /**
     * 查询某医生的请假申请
     */
    List<LeaveRequest> selectByPhysician(@Param("physicianId") Long physicianId);

    /**
     * 查询某医生的请假申请（日期范围）
     */
    List<LeaveRequest> selectByPhysicianAndDateRange(@Param("physicianId") Long physicianId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    /**
     * 查询某日期的请假申请
     */
    List<LeaveRequest> selectByDate(@Param("leaveDate") LocalDate leaveDate);

    /**
     * 查询某状态的请假申请
     */
    List<LeaveRequest> selectByStatus(@Param("status") String status);

    int insert(LeaveRequest request);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("approverId") Long approverId,
                     @Param("approvalTime") java.time.LocalDateTime approvalTime);

    int deleteById(@Param("id") Long id);
}