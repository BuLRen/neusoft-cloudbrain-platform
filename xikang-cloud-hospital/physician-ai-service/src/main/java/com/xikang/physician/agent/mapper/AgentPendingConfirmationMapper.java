package com.xikang.physician.agent.mapper;

import com.xikang.physician.agent.entity.AgentPendingConfirmation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentPendingConfirmationMapper {

    int insert(AgentPendingConfirmation row);

    AgentPendingConfirmation selectByToken(@Param("token") String token);

    int markConsumed(@Param("token") String token);
}
