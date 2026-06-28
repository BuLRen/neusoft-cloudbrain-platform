package com.xikang.registration.mapper;

import com.xikang.registration.dto.MedtechAdminView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMedtechMapper {

    long countMedtechEmployees(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled
    );

    List<MedtechAdminView> selectMedtechEmployeePage(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled,
        @Param("offset") int offset,
        @Param("size") int size
    );

    MedtechAdminView selectMedtechEmployeeById(@Param("id") Long id);

    List<MedtechAdminView> selectMedtechEmployeeAll(
        @Param("departmentId") Long departmentId,
        @Param("keyword") String keyword,
        @Param("includeDisabled") Boolean includeDisabled
    );

    int existsActiveMedtechEmployee(
        @Param("realname") String realname,
        @Param("deptmentId") Long deptmentId
    );
}
