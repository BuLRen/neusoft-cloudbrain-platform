package com.xikang.medtech.util;

/**
 * 解析检查项目是否为 CT 影像，并在 ai_category_code 缺失时按项目编码/名称兜底。
 */
public final class CtCategoryResolver {

    private CtCategoryResolver() {
    }

    public static String resolve(String aiCategoryCode, String techCode, String techName) {
        String code = trim(aiCategoryCode);
        if (code != null && code.startsWith("imaging_ct")) {
            return code;
        }

        String normalizedTechCode = trim(techCode);
        if (normalizedTechCode != null) {
            if ("XJCT".equalsIgnoreCase(normalizedTechCode)) {
                return "imaging_ct_chest";
            }
            if ("TLCT".equalsIgnoreCase(normalizedTechCode)) {
                return "imaging_ct_brain";
            }
        }

        String name = trim(techName);
        if (name != null && containsCtMarker(name)) {
            if (matchesBrain(name)) {
                return "imaging_ct_brain";
            }
            if (matchesChest(name)) {
                return "imaging_ct_chest";
            }
            return "imaging_ct";
        }

        return code;
    }

    public static boolean isCt(String aiCategoryCode, String techCode, String techName) {
        String resolved = resolve(aiCategoryCode, techCode, techName);
        return resolved != null && resolved.startsWith("imaging_ct");
    }

    private static boolean containsCtMarker(String name) {
        return name.toUpperCase().contains("CT");
    }

    private static boolean matchesBrain(String name) {
        return name.contains("脑") || name.contains("颅") || name.contains("头颅");
    }

    private static boolean matchesChest(String name) {
        return name.contains("胸") || name.contains("肺");
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
