package com.xikang.physician.simulation.mapper;

import com.xikang.physician.simulation.entity.SimulationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulationConfigMapper {

    SimulationConfig selectBestMatch(
        @Param("techCode") String techCode,
        @Param("checkName") String checkName
    );
}
