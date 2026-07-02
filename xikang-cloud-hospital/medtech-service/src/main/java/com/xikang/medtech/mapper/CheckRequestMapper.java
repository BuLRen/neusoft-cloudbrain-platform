package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.CheckRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Check Request Mapper
 */
@Mapper
public interface CheckRequestMapper {

    CheckRequest selectById(Long id);

    List<CheckRequest> selectByRegisterId(
        @Param("registerId") Long registerId,
        @Param("departmentId") Long departmentId
    );

    List<CheckRequest> selectByCheckState(
        @Param("checkState") String checkState,
        @Param("departmentId") Long departmentId
    );

    List<CheckRequest> selectPending(@Param("departmentId") Long departmentId);

    int updateCheckState(@Param("id") Long id, @Param("checkState") String checkState);

    int updateCheckStateWithEmployee(
        @Param("id") Long id,
        @Param("checkState") String checkState,
        @Param("checkEmployeeId") Long checkEmployeeId
    );

    int updateResult(CheckRequest checkRequest);

    int updateArchive(
        @Param("id") Long id,
        @Param("checkState") String checkState,
        @Param("checkRemark") String checkRemark
    );

    int updateImaging(
        @Param("id") Long id,
        @Param("imagingVolumeId") String imagingVolumeId,
        @Param("imagingUploadedAt") java.time.LocalDateTime imagingUploadedAt,
        @Param("imagingSourceName") String imagingSourceName
    );

    int clearImaging(@Param("id") Long id);
}
