package com.xikang.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonitoringDismissalMapper {

    String selectStatusByAlertKey(@Param("alertKey") String alertKey);

    void upsertDismissal(@Param("alertKey") String alertKey,
                         @Param("status") String status,
                         @Param("operatorId") Long operatorId,
                         @Param("operatorName") String operatorName);
}
