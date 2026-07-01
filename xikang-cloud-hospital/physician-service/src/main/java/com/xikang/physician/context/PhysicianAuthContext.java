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

    /**
     * 确认卡 prepare/consume 的发起人标识：医生用 employeeId，管理员用 userId。
     * （管理员 employeeIdOrNull 恒为 null，不能用于待确认令牌归属。）
     */
    public static Long confirmationActorIdOrNull() {
        Context ctx = get();
        if (ctx == null) {
            return null;
        }
        if (ctx.adminAllAccess()) {
            return ctx.userId();
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
