package com.xikang.registration.mapper;

import com.xikang.registration.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Employee Mapper - 员工Mapper
 */
@Mapper
public interface EmployeeMapper {

    /**
     * 根据科室ID查询医生
     * @param deptmentId 科室ID
     * @return 医生列表
     */
    List<Employee> selectByDepartment(@Param("deptmentId") Long deptmentId);

    /**
     * 根据科室ID查询医生（按挂号级别排序）
     * @param deptmentId 科室ID
     * @return 医生列表（主任医师 > 副主任医师 > 专家号 > 普通号）
     */
    List<Employee> selectByDepartmentOrderByLevel(@Param("deptmentId") Long deptmentId);

    /**
     * 根据科室ID和挂号级别查询医生
     * @param deptmentId 科室ID
     * @param registLevelId 挂号级别ID (1=普通号, 2=专家号, 3=主任医师号)
     * @return 医生列表
     */
    List<Employee> selectByDepartmentAndLevel(@Param("deptmentId") Long deptmentId, @Param("registLevelId") Long registLevelId);

    /**
     * 根据医生ID查询
     * @param id 医生ID
     * @return 医生信息
     */
    Employee selectById(@Param("id") Long id);

    int insert(Employee employee);

    int update(Employee employee);

    int updateDelmark(@Param("id") Long id, @Param("delmark") Integer delmark);

    long countClinicalPhysicians(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled
    );

    java.util.List<com.xikang.registration.dto.PhysicianAdminView> selectClinicalPhysicianPage(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled,
        @Param("offset") int offset,
        @Param("size") int size
    );

    com.xikang.registration.dto.PhysicianAdminView selectClinicalPhysicianById(@Param("id") Long id);

    java.util.List<com.xikang.registration.dto.PhysicianAdminView> selectClinicalPhysicianAll(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled
    );

    int existsActiveClinicalPhysician(
        @Param("realname") String realname,
        @Param("deptmentId") Long deptmentId,
        @Param("registLevelId") Long registLevelId
    );

    int countActiveRegistersByEmployeeId(@Param("employeeId") Long employeeId);
}