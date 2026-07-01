package com.xikang.physician.agent;

/**
 * Thread-local context for Dify Agent custom-tool calls ({@code INTERNAL_AI_TOKEN}).
 * Carries doctor/session identity for access checks and audit.
 */
public final class AgentToolExecutionContext {

    public enum RiskLevel {
        READ, DRAFT, COMMIT
    }

    public record Context(
        Long doctorId,
        Long sessionId,
        String requestId,
        String toolName,
        RiskLevel riskLevel
    ) {
    }

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private AgentToolExecutionContext() {
    }

    public static void enable(Context context) {
        CONTEXT.set(context);
    }

    public static void enable() {
        enable(new Context(null, null, null, null, RiskLevel.READ));
    }

    public static boolean isActive() {
        return CONTEXT.get() != null;
    }

    public static Context get() {
        return CONTEXT.get();
    }

    public static Long getDoctorId() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : ctx.doctorId();
    }

    public static Long getSessionId() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : ctx.sessionId();
    }

    public static String getRequestId() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : ctx.requestId();
    }

    public static String getToolName() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : ctx.toolName();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
