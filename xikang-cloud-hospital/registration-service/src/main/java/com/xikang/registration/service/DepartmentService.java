package com.xikang.registration.service;

import com.xikang.registration.entity.Department;
import com.xikang.registration.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Department Service - 科室服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    /**
     * 获取所有科室
     */
    public List<Department> getAllDepartments() {
        return departmentMapper.selectAll();
    }

    /**
     * 按类型获取科室
     */
    public List<Department> getDepartmentsByType(String type) {
        return departmentMapper.selectByType(type);
    }

    /**
     * 获取科室详情
     */
    public Department getDepartment(Long id) {
        return departmentMapper.selectById(id);
    }

    /**
     * 创建科室
     */
    public Department createDepartment(Department department) {
        departmentMapper.insert(department);
        return department;
    }

    /**
     * 更新科室
     */
    public void updateDepartment(Department department) {
        departmentMapper.update(department);
    }

    /**
     * 删除科室
     */
    public void deleteDepartment(Long id) {
        departmentMapper.deleteById(id);
    }
}
