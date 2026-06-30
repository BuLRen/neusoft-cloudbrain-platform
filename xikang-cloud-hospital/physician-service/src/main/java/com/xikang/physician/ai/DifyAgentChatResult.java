package com.xikang.physician.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DifyAgentChatResult {

    private final String answer;
    private final String conversationId;
    private final List<Map<String, Object>> thoughts;

    public DifyAgentChatResult(String answer, String conversationId, List<Map<String, Object>> thoughts) {
        this.answer = answer == null ? "" : answer;
        this.conversationId = conversationId;
        this.thoughts = thoughts == null ? List.of() : List.copyOf(thoughts);
    }

    public String getAnswer() {
        return answer;
    }

    public String getConversationId() {
        return conversationId;
    }

    public List<Map<String, Object>> getThoughts() {
        return thoughts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final StringBuilder answer = new StringBuilder();
        private String conversationId;
        private final List<Map<String, Object>> thoughts = new ArrayList<>();

        public Builder appendAnswer(String chunk) {
            if (chunk != null && !chunk.isEmpty()) {
                answer.append(chunk);
            }
            return this;
        }

        public Builder conversationId(String conversationId) {
            if (conversationId != null && !conversationId.isBlank()) {
                this.conversationId = conversationId;
            }
            return this;
        }

        public Builder addThought(Map<String, Object> thought) {
            if (thought != null && !thought.isEmpty()) {
                thoughts.add(thought);
            }
            return this;
        }

        public DifyAgentChatResult build() {
            return new DifyAgentChatResult(answer.toString(), conversationId, thoughts);
        }
    }
}
