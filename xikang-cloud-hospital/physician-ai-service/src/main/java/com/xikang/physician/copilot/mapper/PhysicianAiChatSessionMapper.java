package com.xikang.physician.copilot.mapper;

import com.xikang.physician.copilot.entity.PhysicianAiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicianAiChatSessionMapper {

    List<PhysicianAiChatSession> selectByRegisterId(@Param("registerId") Long registerId);

    PhysicianAiChatSession selectById(@Param("id") Long id);

    int insert(PhysicianAiChatSession session);

    int deleteById(@Param("id") Long id);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int touchUpdatedAt(@Param("id") Long id);

    int updateDifyConversationId(
        @Param("id") Long id,
        @Param("difyConversationId") String difyConversationId
    );

    int clearDifyConversationId(@Param("id") Long id);
}
