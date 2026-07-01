package com.xikang.physician.ai;

import java.util.regex.Pattern;

/**
 * Removes model-internal thinking tags from Dify Agent answer streams.
 */
public final class DifyAgentAnswerSanitizer {

    private static final Pattern REDACTED_THINKING = Pattern.compile(
        "<think>[\\s\\S]*?</think>",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern THINK_BLOCK = Pattern.compile(
        Pattern.quote("\u003cthink\u003e") + "[\\s\\S]*?" + Pattern.quote("\u003c/think\u003e"),
        Pattern.CASE_INSENSITIVE
    );
    /** Dify Agent 偶发将 tool 名写入 answer 流，需剔除避免污染聊天气泡 */
    private static final Pattern LEAKED_TOOL_NAME = Pattern.compile(
        "(?<=\\s|^)tool_[a-z0-9_]+(?=\\s|<|$)",
        Pattern.CASE_INSENSITIVE
    );

    private DifyAgentAnswerSanitizer() {
    }

    public static String sanitizeChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }
        String cleaned = REDACTED_THINKING.matcher(chunk).replaceAll("");
        cleaned = THINK_BLOCK.matcher(cleaned).replaceAll("");
        cleaned = LEAKED_TOOL_NAME.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    public static String sanitizeFull(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return sanitizeChunk(text).trim();
    }
}
