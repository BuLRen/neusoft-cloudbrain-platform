package com.xikang.physician.copilot.mapper;

import com.xikang.physician.copilot.entity.PhysicianAiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicianAiChatMessageMapper {

    List<PhysicianAiChatMessage> selectBySession(
        @Param("registerId") Long registerId,
        @Param("sessionId") Long sessionId,
        @Param("limit") int limit
    );

    int insert(PhysicianAiChatMessage message);

    int deleteBySession(
        @Param("registerId") Long registerId,
        @Param("sessionId") Long sessionId
    );

    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
