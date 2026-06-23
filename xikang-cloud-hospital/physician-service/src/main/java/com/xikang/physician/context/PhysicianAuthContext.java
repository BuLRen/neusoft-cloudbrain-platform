package com.xikang.physician.context;

/**
 * 当前请求的医师身份上下文（ThreadLocal）
 */
public final class PhysicianAuthContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private PhysicianAuthContext() {
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

    public static boolean isAdminAllAccess() {
        Context ctx = get();
        return ctx != null && ctx.adminAllAccess();
    }

    public static Long employeeIdOrNull() {
        Context ctx = get();
        if (ctx == null || ctx.adminAllAccess()) {
            return null;
        }
        return ctx.employeeId();
    }

    public record Context(
        Long userId,
        String role,
        Long employeeId,
        boolean adminAllAccess
    ) {
    }
}
