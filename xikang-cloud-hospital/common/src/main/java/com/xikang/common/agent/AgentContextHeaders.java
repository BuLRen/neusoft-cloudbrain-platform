package com.xikang.common.agent;

/**
 * HTTP headers for propagating Dify Agent tool execution context across services.
 */
public final class AgentContextHeaders {

    public static final String DOCTOR_ID = "X-Agent-Doctor-Id";
    public static final String SESSION_ID = "X-Agent-Session-Id";
    public static final String REQUEST_ID = "X-Agent-Request-Id";
    public static final String TOOL_NAME = "X-Agent-Tool";
    public static final String RISK_LEVEL = "X-Agent-Risk";
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    private AgentContextHeaders() {
    }
}
