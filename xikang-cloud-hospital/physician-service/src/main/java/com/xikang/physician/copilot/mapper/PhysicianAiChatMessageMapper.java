package com.xikang.physician.copilot.mapper;

import com.xikang.physician.copilot.entity.PhysicianAiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhysicianAiChatMessageMapper {

    List<PhysicianAiChatMessage> selectByRegisterId(
        @Param("registerId") Long registerId,
        @Param("limit") int limit
    );

    int insert(PhysicianAiChatMessage message);

    int deleteByRegisterId(@Param("registerId") Long registerId);
}
