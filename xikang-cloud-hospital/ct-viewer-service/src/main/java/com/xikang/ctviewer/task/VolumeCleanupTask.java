package com.xikang.ctviewer.task;

import com.xikang.ctviewer.config.CtViewerProperties;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import com.xikang.ctviewer.repository.VolumeMetaRepository;
import com.xikang.ctviewer.service.VolumeStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolumeCleanupTask {

    private final VolumeMetaRepository metaRepository;
    private final VolumeStorageService storageService;
    private final CtViewerProperties properties;

    @Scheduled(fixedDelayString = "${ct-viewer.cleanup-interval-ms:300000}")
    public void cleanupExpiredVolumes() {
        long ttlMs = properties.getVolumeTtlSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Set<String> volumeIds = metaRepository.listAllVolumeIds();
        int removed = 0;

        for (String volumeId : volumeIds) {
            Optional<VolumeMetaDto> metaOpt = metaRepository.findById(volumeId);
            if (metaOpt.isEmpty()) {
                storageService.deleteVolumeDirectory(volumeId);
                metaRepository.delete(volumeId);
                removed++;
                continue;
            }
            VolumeMetaDto meta = metaOpt.get();
            if (meta.getCreatedAtEpochMs() > 0 && now - meta.getCreatedAtEpochMs() > ttlMs) {
                storageService.deleteVolumeDirectory(volumeId);
                metaRepository.delete(volumeId);
                removed++;
            }
        }

        if (removed > 0) {
            log.info("ct-viewer volume cleanup removed {} expired volumes", removed);
        }
    }
}
