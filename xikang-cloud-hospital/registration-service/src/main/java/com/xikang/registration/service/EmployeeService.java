package com.xikang.registration.service;

import com.xikang.registration.entity.Employee;
import com.xikang.registration.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Employee Service - 员工服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    /**
     * 根据科室ID获取医生列表
     */
    public List<Employee> getDoctorsByDepartment(Long departmentId) {
        return employeeMapper.selectByDepartmentOrderByLevel(departmentId);
    }

    /**
     * 根据科室ID和挂号级别获取医生列表
     * @param departmentId 科室ID
     * @param registLevelId 挂号级别ID (1=普通号, 2=专家号, 3=主任医师号)
     */
    public List<Employee> getDoctorsByDepartmentAndLevel(Long departmentId, Long registLevelId) {
        return employeeMapper.selectByDepartmentAndLevel(departmentId, registLevelId);
    }

    /**
     * 根据医生ID获取医生信息
     */
    public Employee getDoctor(Long id) {
        return employeeMapper.selectById(id);
    }
}
