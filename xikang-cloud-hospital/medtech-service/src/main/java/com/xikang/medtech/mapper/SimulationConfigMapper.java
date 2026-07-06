package com.xikang.medtech.mapper;

import com.xikang.medtech.entity.SimulationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SimulationConfigMapper {

    List<SimulationConfig> selectAll(@Param("keyword") String keyword);

    SimulationConfig selectById(@Param("id") Integer id);

    SimulationConfig selectByConfigKey(@Param("configKey") String configKey);

    int insert(SimulationConfig config);

    int update(SimulationConfig config);

    int deleteById(@Param("id") Integer id);
}
