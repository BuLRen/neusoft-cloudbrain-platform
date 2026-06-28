package com.xikang.registration.mapper;

import com.xikang.registration.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Department Mapper
 */
@Mapper
public interface DepartmentMapper {

    List<Department> selectAll();

    List<Department> selectByType(String type);

    Department selectById(Long id);

    Department selectByCode(String code);

    Department selectByName(@Param("name") String name);

    int insert(Department department);

    int update(Department department);

    int deleteById(Long id);
}
