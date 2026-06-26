package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.InspectionRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Inspection Request Mapper
 */
@Mapper
public interface InspectionRequestMapper {

    InspectionRequest selectById(Long id);

    List<InspectionRequest> selectByRegisterId(
        @Param("registerId") Long registerId,
        @Param("departmentId") Long departmentId
    );

    List<InspectionRequest> selectByInspectionState(
        @Param("inspectionState") String inspectionState,
        @Param("departmentId") Long departmentId
    );

    List<InspectionRequest> selectPending(@Param("departmentId") Long departmentId);

    int updateInspectionState(@Param("id") Long id, @Param("inspectionState") String inspectionState);

    int updateInspectionStateWithEmployee(
        @Param("id") Long id,
        @Param("inspectionState") String inspectionState,
        @Param("inspectionEmployeeId") Long inspectionEmployeeId
    );

    int updateInspectionTime(@Param("id") Long id, @Param("inspectionTime") java.time.LocalDateTime inspectionTime);

    int updateResult(InspectionRequest inspectionRequest);

    int updateArchive(
        @Param("id") Long id,
        @Param("inspectionState") String inspectionState,
        @Param("inspectionRemark") String inspectionRemark
    );
}
