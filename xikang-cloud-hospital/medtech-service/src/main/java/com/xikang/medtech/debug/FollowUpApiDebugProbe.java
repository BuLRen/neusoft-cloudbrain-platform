package com.xikang.medtech.debug;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FollowUpApiDebugProbe implements ApplicationRunner {

    private final RequestMappingHandlerMapping handlerMapping;

    public FollowUpApiDebugProbe(
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> glucoseAdviceMappings = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            String pattern = entry.getKey().toString();
            if (pattern.contains("glucose-advice")) {
                glucoseAdviceMappings.add(pattern);
            }
        }
        // #region agent log
        AgentDebugLog.log(
            "A",
            "FollowUpApiDebugProbe:run",
            "medtech-service startup mapping scan",
            Map.of(
                "glucoseAdviceMappingCount",
                glucoseAdviceMappings.size(),
                "glucoseAdviceMappings",
                glucoseAdviceMappings,
                "userDir",
                System.getProperty("user.dir")
            )
        );
        // #endregion
    }
}
