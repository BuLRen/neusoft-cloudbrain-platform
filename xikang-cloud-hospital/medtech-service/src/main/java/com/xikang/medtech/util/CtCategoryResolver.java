package com.xikang.medtech.util;

/**
 * CT 影像项目识别：仅依据医技项目元数据 {@code ai_category_code}，不按名称或编码猜测。
 */
public final class CtCategoryResolver {

    private CtCategoryResolver() {
    }

    public static boolean isCt(String aiCategoryCode) {
        return aiCategoryCode != null && aiCategoryCode.startsWith("imaging_ct");
    }

    public static String normalize(String aiCategoryCode) {
        if (aiCategoryCode == null) {
            return null;
        }
        String trimmed = aiCategoryCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
