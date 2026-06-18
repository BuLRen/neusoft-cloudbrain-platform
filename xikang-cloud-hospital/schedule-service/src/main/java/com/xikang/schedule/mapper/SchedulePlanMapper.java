package com.xikang.schedule.mapper;

import com.xikang.schedule.entity.SchedulePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SchedulePlanMapper {

    List<SchedulePlan> selectAll();

    SchedulePlan selectById(@Param("id") Long id);

    List<SchedulePlan> selectByDepartmentAndMonth(@Param("departmentId") Long departmentId,
                                                   @Param("planMonth") String planMonth);

    List<SchedulePlan> selectByStatus(@Param("status") String status);

    int insert(SchedulePlan plan);

    int update(SchedulePlan plan);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updatePublishInfo(@Param("id") Long id,
                          @Param("publishedTime") java.time.LocalDateTime publishedTime,
                          @Param("publishedBy") Long publishedBy);

    int deleteById(@Param("id") Long id);
}