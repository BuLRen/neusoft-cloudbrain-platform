package com.xikang.physician.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.physician.agent.entity.AgentPendingConfirmation;
import com.xikang.physician.agent.mapper.AgentPendingConfirmationMapper;
import com.xikang.physician.context.PhysicianAuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConfirmationServiceTest {

    @Mock
    private AgentPendingConfirmationMapper pendingConfirmationMapper;

    @InjectMocks
    private AgentConfirmationService confirmationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        confirmationService = new AgentConfirmationService(pendingConfirmationMapper, objectMapper);
        PhysicianAuthContext.set(new PhysicianAuthContext.Context(1L, "physician", 100L, false));
    }

    @AfterEach
    void tearDown() {
        PhysicianAuthContext.clear();
    }

    @Test
    void prepare_shouldCreateToken() {
        Map<String, Object> result = confirmationService.prepare(2001L, 10L, "commit_medical_record",
            Map.of("readme", "头痛3天"));

        assertNotNull(result.get("confirmationToken"));
        assertEquals("commit_medical_record", result.get("actionType"));

        ArgumentCaptor<AgentPendingConfirmation> captor = ArgumentCaptor.forClass(AgentPendingConfirmation.class);
        verify(pendingConfirmationMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getDoctorId());
        assertEquals(2001L, captor.getValue().getRegisterId());
    }

    @Test
    void consume_shouldRejectReusedToken() {
        AgentPendingConfirmation pending = new AgentPendingConfirmation();
        pending.setToken("abc123");
        pending.setRegisterId(2001L);
        pending.setDoctorId(100L);
        pending.setActionType("commit_medical_record");
        pending.setPayloadJson("{\"readme\":\"头痛\"}");
        pending.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        pending.setConsumedAt(LocalDateTime.now());

        when(pendingConfirmationMapper.selectByToken("abc123")).thenReturn(pending);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> confirmationService.consume("abc123", 2001L, Map.of()));
        assertEquals(409, ex.getCode());
    }

    @Test
    void validateActionType_shouldRejectUnknown() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> confirmationService.validateActionType("unknown_action"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void prepare_shouldAllowAdminActor() {
        PhysicianAuthContext.set(new PhysicianAuthContext.Context(99L, "admin", null, true));

        Map<String, Object> result = confirmationService.prepare(2001L, 10L, "commit_medical_record",
            Map.of("allergy", "花生过敏"));

        assertNotNull(result.get("confirmationToken"));

        ArgumentCaptor<AgentPendingConfirmation> captor = ArgumentCaptor.forClass(AgentPendingConfirmation.class);
        verify(pendingConfirmationMapper).insert(captor.capture());
        assertEquals(99L, captor.getValue().getDoctorId());
    }
}
