package com.xikang.ctviewer.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xikang.common.exception.BusinessException;
import com.xikang.ctviewer.config.CtViewerProperties;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class VolumeMetaRepository {

    private static final String KEY_PREFIX = "ct-viewer:volume:";
    private static final String INDEX_KEY = "ct-viewer:volume:index";

    private final StringRedisTemplate redisTemplate;
    private final CtViewerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void save(VolumeMetaDto meta) {
        save(meta, resolveTtl(meta));
    }

    public void savePersistent(VolumeMetaDto meta) {
        save(meta, null);
    }

    private void save(VolumeMetaDto meta, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(meta);
            String key = key(meta.getVolumeId());
            if (ttl == null) {
                redisTemplate.opsForValue().set(key, json);
            } else {
                redisTemplate.opsForValue().set(key, json, ttl);
            }
            redisTemplate.opsForSet().add(INDEX_KEY, meta.getVolumeId());
        } catch (JsonProcessingException ex) {
            throw new BusinessException(500, "体数据元信息序列化失败", ex);
        }
    }

    private Duration resolveTtl(VolumeMetaDto meta) {
        if (meta != null && meta.getBoundCheckRequestId() != null) {
            return null;
        }
        return Duration.ofSeconds(properties.getVolumeTtlSeconds());
    }

    public boolean isPersistent(VolumeMetaDto meta) {
        return meta != null && meta.getBoundCheckRequestId() != null;
    }

    public Optional<VolumeMetaDto> findById(String volumeId) {
        String json = redisTemplate.opsForValue().get(key(volumeId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, VolumeMetaDto.class));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse volume meta | volumeId={}", volumeId, ex);
            return Optional.empty();
        }
    }

    public VolumeMetaDto requireById(String volumeId) {
        return findById(volumeId)
            .orElseThrow(() -> new BusinessException(404, "volume_id 不存在"));
    }

    public void delete(String volumeId) {
        redisTemplate.delete(key(volumeId));
        redisTemplate.opsForSet().remove(INDEX_KEY, volumeId);
    }

    public Set<String> listAllVolumeIds() {
        Set<String> ids = redisTemplate.opsForSet().members(INDEX_KEY);
        return ids == null ? Set.of() : ids;
    }

    private String key(String volumeId) {
        return KEY_PREFIX + volumeId;
    }
}
