package com.xikang.medtech.service;

import com.xikang.medtech.mapper.FollowUpDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpEnrollmentSyncService {

    private final FollowUpDashboardMapper followUpDashboardMapper;

    /** 独立事务写入 C 类表；表不存在时不影响外层 profile 写入。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySyncEnrollment(Map<String, Object> payload) {
        try {
            followUpDashboardMapper.upsertEnrollment(payload);
        } catch (DataAccessException ex) {
            log.warn("follow_up_enrollment 写入失败（可能尚未执行 migrate_020）: {}", ex.getMessage());
        }
    }
}
