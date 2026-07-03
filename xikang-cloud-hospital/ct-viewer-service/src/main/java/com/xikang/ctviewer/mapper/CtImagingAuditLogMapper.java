package com.xikang.ctviewer.mapper;

import com.xikang.ctviewer.entity.CtImagingAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CtImagingAuditLogMapper {

    int insert(CtImagingAuditLog row);

    long countByFilters(
        @Param("volumeId") String volumeId,
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("success") Boolean success
    );

    List<CtImagingAuditLog> selectByFilters(
        @Param("volumeId") String volumeId,
        @Param("userId") Long userId,
        @Param("action") String action,
        @Param("success") Boolean success,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}
