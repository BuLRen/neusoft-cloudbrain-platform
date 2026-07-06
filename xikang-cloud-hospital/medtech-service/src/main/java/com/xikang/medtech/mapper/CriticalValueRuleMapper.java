package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.CriticalValueRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CriticalValueRuleMapper {

    List<CriticalValueRule> selectAllEnabled();
}
