package com.xikang.ai.consult.ai.memory;

import com.xikang.ai.consult.ai.util.PromptUtils;
import com.xikang.ai.consult.entity.AiPreVisitRecord;
import com.xikang.ai.consult.mapper.AiPreVisitRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 预问诊对话记忆仓库 —— 基于 ai_consultation_record 表实现 Spring AI {@link ChatMemoryRepository}。
 *
 * <p>本仓库采用"<b>读托管 / 写不托管</b>"的半托管模式：
 * <ul>
 *   <li><b>读</b>（{@link #findByConversationId}）：把 ai_consultation_record 表中同一 sessionUuid
 *       的多行（每行一轮）按时间顺序还原为 Spring AI 的 {@link Message} 序列。
 *       conversationId 即业务层的 sessionUuid。</li>
 *   <li><b>写</b>（{@link #saveAll}）：空实现。写入仍由 {@code PreConsultService} 通过 mapper 逐轮 insert，
 *       以保留"一行 = 一轮对话 + 汇总字段"的业务语义。</li>
 * </ul>
 *
 * <p>这是 Spring AI 1.0 允许的合法用法：当业务表为权威数据源、memory 仅用于"读历史"场景时，
 * 写入可由业务层自行管理。其他需要访问预问诊历史的模块（如医生工作站、AI 诊断服务）
 * 可直接注入本接口调用 {@link #findByConversationId}，无需关心底层表结构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreVisitChatMemoryRepository implements ChatMemoryRepository {

    private final AiPreVisitRecordMapper mapper;

    @Override
    public List<String> findConversationIds() {
        // 当前没有"枚举所有会话"的业务需求，返回空列表。
        // Spring AI 1.0 的 ChatMemoryRepository 接口要求实现此方法，但不强制语义。
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<AiPreVisitRecord> rows = mapper.selectBySessionUuid(conversationId);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>(rows.size() * 2);
        for (AiPreVisitRecord r : rows) {
            if (r.getAiQuestion() != null && !r.getAiQuestion().isBlank()) {
                messages.add(new AssistantMessage(PromptUtils.cleanDoneToken(r.getAiQuestion())));
            }
            if (r.getPatientAnswer() != null && !r.getPatientAnswer().isBlank()) {
                messages.add(new UserMessage(r.getPatientAnswer()));
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 空实现：写入仍由 PreConsultService 走 mapper.insert，保留"一行=一轮"的业务语义。
        // 详见类注释。
        log.debug("saveAll 被调用但未执行（半托管模式，写入由 service 负责）。conversationId={}", conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // 当前业务暂无删除会话需求，保留扩展点。
        log.debug("deleteByConversationId 未实现。conversationId={}", conversationId);
    }
}
