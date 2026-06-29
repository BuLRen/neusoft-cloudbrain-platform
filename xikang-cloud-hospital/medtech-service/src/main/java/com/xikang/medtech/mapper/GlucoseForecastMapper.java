package com.xikang.medtech.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface GlucoseForecastMapper {

    boolean isGlucoseCohortPatient(@Param("registerId") Long registerId);

    List<Map<String, Object>> selectObservationsForPrediction(@Param("registerId") Long registerId);

    int deleteForecasts(@Param("registerId") Long registerId, @Param("metricCode") String metricCode);

    int insertForecast(Map<String, Object> row);

    List<Map<String, Object>> selectForecasts(
        @Param("registerId") Long registerId,
        @Param("metricCode") String metricCode,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    Map<String, Object> selectLatestForecastMeta(@Param("registerId") Long registerId, @Param("metricCode") String metricCode);
}
