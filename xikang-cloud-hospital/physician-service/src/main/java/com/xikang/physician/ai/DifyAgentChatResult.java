package com.xikang.physician.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        /** 以 Dify agent_thought 的 id/position 为键，合并同一条思考的多次增量事件 */
        private final Map<String, Map<String, Object>> thoughtsByKey = new LinkedHashMap<>();

        public Builder appendAnswer(String chunk) {
            if (chunk != null && !chunk.isEmpty()) {
                answer.append(chunk);
            }
            return this;
        }

        /**
         * Dify 流式 answer 可能是增量片段，也可能是累计全文；返回应追加的增量部分。
         */
        public String resolveAnswerDelta(String incoming) {
            if (incoming == null || incoming.isEmpty()) {
                return "";
            }
            String current = answer.toString();
            if (incoming.startsWith(current)) {
                return incoming.substring(current.length());
            }
            if (current.startsWith(incoming)) {
                return "";
            }
            return incoming;
        }

        public Builder conversationId(String conversationId) {
            if (conversationId != null && !conversationId.isBlank()) {
                this.conversationId = conversationId;
            }
            return this;
        }

        /**
         * 合并一次 agent_thought 增量事件；返回合并后的完整思考快照（供流式转发给前端）。
         * Dify 会针对同一条思考（相同 id/position）多次下发，先给 tool/tool_input，再补 observation，
         * 因此必须按键合并，避免拆散或丢失 observation。
         */
        public Map<String, Object> mergeThought(Map<String, Object> incoming) {
            if (incoming == null || incoming.isEmpty()) {
                return Map.of();
            }
            String key = thoughtKey(incoming);
            Map<String, Object> existing = thoughtsByKey.get(key);
            if (existing == null) {
                existing = new LinkedHashMap<>();
                thoughtsByKey.put(key, existing);
            }
            for (Map.Entry<String, Object> entry : incoming.entrySet()) {
                Object value = entry.getValue();
                if (value != null && !String.valueOf(value).isBlank()) {
                    existing.put(entry.getKey(), value);
                }
            }
            return new LinkedHashMap<>(existing);
        }

        private String thoughtKey(Map<String, Object> thought) {
            Object id = thought.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return "id:" + id;
            }
            Object position = thought.get("position");
            if (position != null && !String.valueOf(position).isBlank()) {
                return "pos:" + position;
            }
            return "seq:" + thoughtsByKey.size();
        }

        public DifyAgentChatResult build() {
            return new DifyAgentChatResult(answer.toString(), conversationId,
                new ArrayList<>(thoughtsByKey.values()));
        }
    }
}
