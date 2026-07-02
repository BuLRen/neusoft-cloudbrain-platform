package com.xikang.ctviewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VolumeMetaDto {
    private String volumeId;
    private String nrrdPath;
    private String sourceName;
    private boolean mask;
    private String seriesId;
    private int fileCount;
    private long createdAtEpochMs;

    private List<Integer> shapeZyx;
    private List<Integer> sizeXyz;
    private List<Double> spacingXyz;
    private Double min;
    private Double max;

    @SuppressWarnings("unchecked")
    public static VolumeMetaDto fromAlgoMeta(String volumeId, String nrrdPath, Map<String, Object> meta, long createdAt) {
        VolumeMetaDto dto = new VolumeMetaDto();
        dto.setVolumeId(volumeId);
        dto.setNrrdPath(nrrdPath);
        dto.setCreatedAtEpochMs(createdAt);
        if (meta == null) {
            return dto;
        }
        dto.setSourceName(stringVal(meta.get("source_name")));
        dto.setMask(Boolean.TRUE.equals(meta.get("is_mask")));
        dto.setSeriesId(stringVal(meta.get("series_id")));
        Object fileCount = meta.get("file_count");
        if (fileCount instanceof Number number) {
            dto.setFileCount(number.intValue());
        }
        Object shape = meta.get("shape_zyx");
        if (shape instanceof List<?> list) {
            dto.setShapeZyx(toIntList(list));
        }
        Object size = meta.get("size_xyz");
        if (size instanceof List<?> list) {
            dto.setSizeXyz(toIntList(list));
        }
        Object spacing = meta.get("spacing_xyz");
        if (spacing instanceof List<?> list) {
            dto.setSpacingXyz(toDoubleList(list));
        }
        Object min = meta.get("min");
        if (min instanceof Number number) {
            dto.setMin(number.doubleValue());
        }
        Object max = meta.get("max");
        if (max instanceof Number number) {
            dto.setMax(number.doubleValue());
        }
        return dto;
    }

    public Map<String, Object> toFrontendMeta() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("shape_zyx", shapeZyx);
        map.put("size_xyz", sizeXyz);
        map.put("spacing_xyz", spacingXyz);
        map.put("min", min);
        map.put("max", max);
        map.put("is_mask", mask);
        map.put("source_name", sourceName);
        map.put("series_id", seriesId);
        map.put("file_count", fileCount);
        return map;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<Integer> toIntList(List<?> list) {
        return list.stream().map(item -> ((Number) item).intValue()).toList();
    }

    private static List<Double> toDoubleList(List<?> list) {
        return list.stream().map(item -> ((Number) item).doubleValue()).toList();
    }
}
