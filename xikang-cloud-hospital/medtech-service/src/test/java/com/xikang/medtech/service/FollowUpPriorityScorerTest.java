package com.xikang.medtech.service;

import com.xikang.medtech.dto.FollowUpPriorityResult;
import com.xikang.medtech.dto.FollowUpPriorityScorerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowUpPriorityScorerTest {

    private FollowUpPriorityScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new FollowUpPriorityScorer(null, null, null);
    }

    @Test
    void oncologyDepartmentIsCritical() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .departmentId(18)
                .departmentName("肿瘤科")
                .build()
        );
        assertEquals("critical", result.getPriorityLevel());
        assertEquals(3, result.getInterviewIntervalDays());
        assertTrue(result.getMatchedRules().contains("oncology_department"));
    }

    @Test
    void malignancyDiagnosisIsCritical() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .diagnosisText("考虑肺恶性肿瘤")
                .build()
        );
        assertEquals("critical", result.getPriorityLevel());
    }

    @Test
    void threeHighLabFlagsAreCritical() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .highAbnormalCount(3)
                .anyAbnormalCount(3)
                .build()
        );
        assertEquals("critical", result.getPriorityLevel());
    }

    @Test
    void chronicDeptWithChronicDiagnosisIsHigh() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .departmentName("内分泌科")
                .diagnosisText("2型糖尿病")
                .build()
        );
        assertEquals("high", result.getPriorityLevel());
        assertEquals(7, result.getInterviewIntervalDays());
    }

    @Test
    void threeDrugsIsHigh() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .distinctDrugCount(3)
                .build()
        );
        assertEquals("high", result.getPriorityLevel());
    }

    @Test
    void oneLabAbnormalIsHigh() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .anyAbnormalCount(1)
                .build()
        );
        assertEquals("high", result.getPriorityLevel());
    }

    @Test
    void defaultIsNormal() {
        FollowUpPriorityResult result = scorer.score(
            FollowUpPriorityScorerContext.builder()
                .departmentName("普通内科")
                .diagnosisText("上呼吸道感染")
                .distinctDrugCount(1)
                .build()
        );
        assertEquals("normal", result.getPriorityLevel());
        assertEquals(14, result.getInterviewIntervalDays());
    }
}
