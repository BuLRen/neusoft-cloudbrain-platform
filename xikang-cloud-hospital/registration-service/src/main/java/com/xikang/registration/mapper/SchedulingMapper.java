package com.xikang.registration.mapper;

import com.xikang.registration.entity.Scheduling;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduling Mapper
 */
@Mapper
public interface SchedulingMapper {

    Scheduling selectById(Long id);

    List<Scheduling> selectByDepartmentId(Long departmentId);

    List<Scheduling> selectByDate(LocalDate date);

    List<Scheduling> selectByDepartmentAndDate(Long departmentId, LocalDate date);

    List<Scheduling> selectAvailableByDepartmentAndDate(Long departmentId, LocalDate date);

    List<Scheduling> selectByPhysicianAndDateRange(Long physicianId, LocalDate startDate, LocalDate endDate);

    int insert(Scheduling scheduling);

    int update(Scheduling scheduling);

    int updateStatus(Long id, Integer status);

    int updateUsedQuota(Long id, Integer usedQuota);

    int deleteById(Long id);
}
