package com.xikang.medtech.dto;

import java.util.ArrayList;
import java.util.List;

public class FollowUpPriorityResult {

    private final String priorityLevel;
    private final int interviewIntervalDays;
    private final int observationIntervalDays;
    private final List<String> matchedRules;

    public FollowUpPriorityResult(
        String priorityLevel,
        int interviewIntervalDays,
        int observationIntervalDays,
        List<String> matchedRules
    ) {
        this.priorityLevel = priorityLevel;
        this.interviewIntervalDays = interviewIntervalDays;
        this.observationIntervalDays = observationIntervalDays;
        this.matchedRules = matchedRules == null ? List.of() : List.copyOf(matchedRules);
    }

    public static FollowUpPriorityResult of(String priorityLevel, List<String> matchedRules) {
        return switch (priorityLevel) {
            case "critical" -> new FollowUpPriorityResult("critical", 3, 1, matchedRules);
            case "high" -> new FollowUpPriorityResult("high", 7, 1, matchedRules);
            default -> new FollowUpPriorityResult("normal", 14, 1, matchedRules);
        };
    }

    public String getPriorityLevel() {
        return priorityLevel;
    }

    public int getInterviewIntervalDays() {
        return interviewIntervalDays;
    }

    public int getObservationIntervalDays() {
        return observationIntervalDays;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }

    public List<String> matchedRulesMutable() {
        return new ArrayList<>(matchedRules);
    }
}
