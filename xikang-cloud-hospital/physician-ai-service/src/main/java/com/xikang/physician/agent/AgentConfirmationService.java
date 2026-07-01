package com.xikang.physician.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.physician.agent.entity.AgentPendingConfirmation;
import com.xikang.physician.agent.mapper.AgentPendingConfirmationMapper;
import com.xikang.physician.context.PhysicianAuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentConfirmationService {

    public static final Set<String> COMMIT_ACTION_TYPES = Set.of(
        "commit_medical_record",
        "commit_preliminary_diagnosis",
        "commit_check_requests",
        "commit_inspection_requests",
        "commit_disposal_requests",
        "commit_diagnosis",
        "commit_prescription",
        "commit_archive_visit"
    );

    private static final int TOKEN_TTL_MINUTES = 30;

    private final AgentPendingConfirmationMapper pendingConfirmationMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> prepare(
        Long registerId,
        Long sessionId,
        String actionType,
        Map<String, Object> payload
    ) {
        validateActionType(actionType);
        Long doctorId = requireDoctorId();
        String token = UUID.randomUUID().toString().replace("-", "");

        AgentPendingConfirmation row = new AgentPendingConfirmation();
        row.setToken(token);
        row.setRegisterId(registerId);
        row.setDoctorId(doctorId);
        row.setSessionId(sessionId);
        row.setActionType(actionType.trim());
        row.setPayloadJson(toJson(payload));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES));
        row.setCreatedAt(LocalDateTime.now());
        pendingConfirmationMapper.insert(row);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmationToken", token);
        result.put("actionType", actionType);
        result.put("registerId", registerId);
        result.put("expiresAt", row.getExpiresAt());
        result.put("payload", payload);
        return result;
    }

    @Transactional
    public Map<String, Object> consume(
        String token,
        Long registerId,
        Map<String, Object> payloadOverride
    ) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(400, "confirmation_token 不能为空");
        }
        AgentPendingConfirmation pending = pendingConfirmationMapper.selectByToken(token.trim());
        if (pending == null) {
            throw new BusinessException(404, "确认令牌无效或已过期");
        }
        if (pending.getConsumedAt() != null) {
            throw new BusinessException(409, "确认令牌已使用");
        }
        if (pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(410, "确认令牌已过期，请重新生成");
        }
        if (!registerId.equals(pending.getRegisterId())) {
            throw new BusinessException(403, "确认令牌与就诊号不匹配");
        }
        Long doctorId = requireDoctorId();
        if (!doctorId.equals(pending.getDoctorId()) && !PhysicianAuthContext.isAdminAllAccess()) {
            throw new BusinessException(403, "确认令牌与当前医生不匹配");
        }

        int updated = pendingConfirmationMapper.markConsumed(token.trim());
        if (updated == 0) {
            throw new BusinessException(409, "确认令牌已使用");
        }

        Map<String, Object> payload = parsePayload(pending.getPayloadJson());
        if (payloadOverride != null && !payloadOverride.isEmpty()) {
            payload.putAll(payloadOverride);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionType", pending.getActionType());
        result.put("registerId", pending.getRegisterId());
        result.put("sessionId", pending.getSessionId());
        result.put("confirmationToken", token.trim());
        result.put("payload", payload);
        return result;
    }

    public void validateActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            throw new BusinessException(400, "actionType 不能为空");
        }
        if (!COMMIT_ACTION_TYPES.contains(actionType.trim())) {
            throw new BusinessException(400, "不支持的确认操作类型: " + actionType);
        }
    }

    private Long requireDoctorId() {
        Long actorId = PhysicianAuthContext.confirmationActorIdOrNull();
        if (actorId == null) {
            throw new BusinessException(403, "未登录或无权操作");
        }
        return actorId;
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(400, "待确认载荷解析失败");
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            throw new BusinessException(400, "载荷序列化失败");
        }
    }
}
