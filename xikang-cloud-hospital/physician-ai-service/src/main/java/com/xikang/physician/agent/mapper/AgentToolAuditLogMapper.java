package com.xikang.physician.agent.mapper;

import com.xikang.physician.agent.entity.AgentToolAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentToolAuditLogMapper {

    int insert(AgentToolAuditLog row);

    List<AgentToolAuditLog> selectCopilotConfirmCompletions(
        @Param("registerId") Long registerId,
        @Param("sessionId") Long sessionId
    );
}
