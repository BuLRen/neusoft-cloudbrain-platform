package com.xikang.physician.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Triggers W3 analysis asynchronously after medtech result submission.
 */
@Service
public class W3AutoTriggerService {

    private static final Logger log = LoggerFactory.getLogger(W3AutoTriggerService.class);

    private final PhysicianAiPipelineService pipelineService;

    public W3AutoTriggerService(PhysicianAiPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Async
    public void triggerW3(Long registerId) {
        if (registerId == null) {
            return;
        }
        try {
            pipelineService.runW3(registerId);
            log.info("Async W3 completed | registerId={}", registerId);
        } catch (Exception ex) {
            log.warn("Async W3 failed | registerId={} reason={}", registerId, ex.getMessage());
        }
    }
}
