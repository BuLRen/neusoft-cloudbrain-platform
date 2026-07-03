package com.xikang.registration.mapper;

import com.xikang.registration.dto.FollowUpAdminView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminFollowUpMapper {

    long countFollowUpEmployees(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled
    );

    List<FollowUpAdminView> selectFollowUpEmployeePage(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled,
        @Param("offset") int offset,
        @Param("size") int size
    );

    FollowUpAdminView selectFollowUpEmployeeById(@Param("id") Long id);
}
