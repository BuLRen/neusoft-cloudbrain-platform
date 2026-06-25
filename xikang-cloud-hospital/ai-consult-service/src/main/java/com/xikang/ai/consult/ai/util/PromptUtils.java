package com.xikang.ai.consult.ai.util;

/**
 * Prompt / Message 处理工具
 */
public final class PromptUtils {

    /** 预问诊结束标记，AI 在 5 项信息采集完毕后追加在回复末尾 */
    public static final String DONE_TOKEN = "<<PRECONSULT_DONE>>";

    private PromptUtils() {}

    /**
     * 清理预问诊结束标记 {@value #DONE_TOKEN}（完整字符串替换）。
     * 该标记由 AI 在 5 项信息全部采集完毕后追加在回复末尾，
     * 写入数据库或拼装历史消息前需剥离，避免污染下一轮 prompt。
     */
    public static String cleanDoneToken(String text) {
        return text == null ? "" : text.replace(DONE_TOKEN, "").trim();
    }

    /**
     * 判断 AI 回复是否表示预问诊"真正结束"。
     *
     * <p>必须同时满足两个条件：
     * <ol>
     *   <li>回复包含 {@value #DONE_TOKEN} 标记（AI 自己声明结束）；</li>
     *   <li>清理标记后的回复文本中**不包含任何问号**（中英文）——即不再向患者提问。</li>
     * </ol>
     *
     * <p>第二个条件是代码层兜底，防止 AI 在追问句末尾误输出 DONE 标记
     * （典型 bug："除了青霉素还有别的过敏吗？\n&lt;&lt;PRECONSULT_DONE&gt;&gt;"）。
     * 这种情况下虽然 AI 输出了标记，但它实际上还在等患者回答，
     * 不应判定为结束——否则患者会困惑"它怎么不说话了"。
     *
     * @param aiReply AI 的原始回复（含可能的 DONE 标记）
     * @return true 当且仅当 AI 明确声明结束且不再提问
     */
    public static boolean isConsultationFinished(String aiReply) {
        if (aiReply == null || !aiReply.contains(DONE_TOKEN)) {
            return false;
        }
        // 清理标记后再检查问号，避免标记本身被误判（标记里无问号，但稳妥起见先清理）
        String cleaned = aiReply.replace(DONE_TOKEN, "");
        return !cleaned.contains("?") && !cleaned.contains("？");
    }

    /**
     * 流式分片过滤器：处理"标记可能跨 chunk 分片"的场景。
     *
     * <p>每次新 chunk 到来时，把它追加到内部 buffer，然后：
     * <ol>
     *   <li>把 buffer 里所有完整的 {@value #DONE_TOKEN} 替换为空；</li>
     *   <li>检查 buffer 末尾是否是 {@value #DONE_TOKEN} 的某个前缀（如 {@code <<}、{@code <<PRE} 等），
     *       如果是，把这部分暂存在 buffer 里不返回，等下个 chunk 来了再判断
     *       （因为前缀可能是完整标记的开头，需要等后续字符确认）；</li>
     *   <li>返回 buffer 里可以安全下发的部分。</li>
     * </ol>
     *
     * <p>流结束时调用 {@link #flush()} 取出 buffer 里剩余的全部内容（此时不会再有后续 chunk，
     * 所以剩余的即使像前缀也只是普通文本，安全下发）。
     *
     * <p>典型用法（在 callAi 的 doOnNext 回调里）：
     * <pre>{@code
     * DoneTokenStreamFilter filter = new DoneTokenStreamFilter();
     * // 每个 chunk：
     * String safe = filter.accept(chunk);
     * if (!safe.isEmpty()) tokenConsumer.accept(safe);
     * // 流结束：
     * String tail = filter.flush();
     * if (!tail.isEmpty()) tokenConsumer.accept(tail);
     * }</pre>
     */
    public static class DoneTokenStreamFilter {
        private final StringBuilder buf = new StringBuilder();
        private static final int TOKEN_LEN = DONE_TOKEN.length();

        /** 处理一个新 chunk，返回可以安全下发的前段文本（可能为空） */
        public String accept(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }
            buf.append(chunk);

            // 1. 先把 buffer 里所有完整的标记替换为空
            int idx;
            while ((idx = buf.indexOf(DONE_TOKEN)) >= 0) {
                buf.delete(idx, idx + TOKEN_LEN);
            }

            // 2. 找到 buf 的最长后缀，使该后缀是 DONE_TOKEN 的前缀。
            //    这段后缀可能是"等待续接的标记开头"，需要保留在 buf 里等下个 chunk；
            //    它前面的部分可以安全下发。
            //
            //    注意：不能只看"末尾 N 字符"是否等于 DONE_TOKEN 前 N 字符
            //    （那种写法在跨 3 chunk 切分时会失效，详见场景 8）。
            //    必须用 startsWith(buf 的后缀) 来判断，从最长到最短试。
            int safeEnd = buf.length();
            int maxPrefixLen = Math.min(TOKEN_LEN - 1, buf.length());
            for (int prefixLen = maxPrefixLen; prefixLen >= 1; prefixLen--) {
                String suffix = buf.substring(buf.length() - prefixLen);
                if (DONE_TOKEN.startsWith(suffix)) {
                    safeEnd = buf.length() - prefixLen;
                    break;
                }
            }

            if (safeEnd <= 0) {
                // 整个 buffer 都是潜在标记前缀（如 "<<PRE" 或 "<<PRECONSULT_DONE"），等下个 chunk
                return "";
            }
            String safe = buf.substring(0, safeEnd);
            buf.delete(0, safeEnd);
            return safe;
        }

        /** 流结束时调用，取出 buffer 里剩余的全部内容（此时不会再有后续 chunk，安全下发） */
        public String flush() {
            if (buf.length() == 0) {
                return "";
            }
            String rest = buf.toString();
            buf.setLength(0);
            return rest;
        }
    }
}
