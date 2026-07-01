package com.xikang.medtech.context;

import java.util.Collections;
import java.util.List;

/**
 * 患者随访端身份上下文（ThreadLocal）
 */
public final class PatientFollowUpAuthContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private PatientFollowUpAuthContext() {
    }

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long userIdOrNull() {
        Context ctx = get();
        return ctx != null ? ctx.userId() : null;
    }

    public static List<Long> patientIdsOrEmpty() {
        Context ctx = get();
        return ctx != null ? ctx.patientIds() : List.of();
    }

    public static Long primaryPatientIdOrNull() {
        List<Long> ids = patientIdsOrEmpty();
        return ids.isEmpty() ? null : ids.get(0);
    }

    public record Context(Long userId, String role, List<Long> patientIds) {
        public Context {
            patientIds = patientIds != null ? List.copyOf(patientIds) : Collections.emptyList();
        }
    }
}
