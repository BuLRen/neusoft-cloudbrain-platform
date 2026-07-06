package com.xikang.medtech.context;

public final class CriticalValueAuthContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private CriticalValueAuthContext() {
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

    public static boolean isAdmin() {
        Context ctx = get();
        return ctx != null && "admin".equals(ctx.role());
    }

    public static Long employeeIdOrNull() {
        Context ctx = get();
        return ctx == null ? null : ctx.employeeId();
    }

    public record Context(Long userId, String role, Long employeeId, String realName) {
    }
}
