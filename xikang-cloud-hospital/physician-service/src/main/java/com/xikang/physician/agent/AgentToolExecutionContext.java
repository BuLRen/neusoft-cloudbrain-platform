package com.xikang.physician.agent;

/**
 * Marks the current thread as executing a Dify Agent custom-tool call (INTERNAL_AI_TOKEN).
 * {@link com.xikang.physician.service.PhysicianService} skips per-doctor access checks while active.
 */
public final class AgentToolExecutionContext {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private AgentToolExecutionContext() {
    }

    public static void enable() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void clear() {
        ACTIVE.remove();
    }
}
