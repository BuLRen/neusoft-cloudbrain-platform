package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface HealthObservationMapper {

    List<Map<String, Object>> selectObservations(
        @Param("registerId") Long registerId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        @Param("metricKeys") List<String> metricKeys,
        @Param("sourceType") String sourceType,
        @Param("sourceTypes") List<String> sourceTypes
    );

    List<Map<String, Object>> selectLabMetricMappings();

    List<Map<String, Object>> selectInspectionResults(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectCheckResults(@Param("registerId") Long registerId);

    LocalDateTime selectLatestPatientGlucoseObservationAt(@Param("registerId") Long registerId);

    Double selectLatestPatientGlucoseValue(@Param("registerId") Long registerId);

    int insertObservation(
        @Param("registerId") Long registerId,
        @Param("observedAt") LocalDateTime observedAt,
        @Param("metricCode") String metricCode,
        @Param("metricValue") double metricValue,
        @Param("unit") String unit,
        @Param("sourceType") String sourceType,
        @Param("sourceRefId") Long sourceRefId,
        @Param("note") String note
    );
}
