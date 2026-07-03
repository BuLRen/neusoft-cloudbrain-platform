package com.xikang.medtech.constants;

public final class FollowUpDepartmentConstants {

    public static final int ENDOCRINE_DEPARTMENT_ID = 7;

    private FollowUpDepartmentConstants() {
    }

    public static boolean isEndocrine(Integer departmentId) {
        return departmentId != null && departmentId == ENDOCRINE_DEPARTMENT_ID;
    }
}
