package com.xikang.ctviewer.context;

/**
 * 当前请求的 CT 影像访问身份上下文（ThreadLocal）
 */
public final class CtViewerAuthContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private CtViewerAuthContext() {
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

    public static Long userIdOrNull() {
        Context ctx = get();
        return ctx == null ? null : ctx.userId();
    }

    public static Long employeeIdOrNull() {
        Context ctx = get();
        if (ctx == null || ctx.adminAllAccess()) {
            return null;
        }
        return ctx.employeeId();
    }

    public static Long departmentIdOrNull() {
        Context ctx = get();
        if (ctx == null || ctx.adminAllAccess()) {
            return null;
        }
        return ctx.departmentId();
    }

    public record Context(
        Long userId,
        String role,
        Long employeeId,
        Long departmentId,
        String departmentName,
        boolean adminAllAccess
    ) {
    }
}
