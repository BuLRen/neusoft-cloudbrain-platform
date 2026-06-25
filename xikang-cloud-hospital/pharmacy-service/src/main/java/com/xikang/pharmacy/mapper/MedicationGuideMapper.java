package com.xikang.pharmacy.mapper;

import com.xikang.pharmacy.entity.MedicationGuide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用药指导单 Mapper。
 */
@Mapper
public interface MedicationGuideMapper {

    /** 插入一条记录。guide_content 由 service 用 ObjectMapper 序列化为 JSON 字符串后传入。 */
    int insert(MedicationGuide guide);

    /** 取该挂号下最新一条指导单（按 id desc）。 */
    MedicationGuide selectLatestByRegisterId(@Param("registerId") Long registerId);

    /** 取该挂号下全部指导单（重试历史）。 */
    List<MedicationGuide> selectByRegisterId(@Param("registerId") Long registerId);
}
