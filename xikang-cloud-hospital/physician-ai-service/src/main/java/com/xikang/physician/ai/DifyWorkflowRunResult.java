package com.xikang.physician.ai;

import java.util.Map;

public class DifyWorkflowRunResult {

    private final String status;
    private final Map<String, Object> outputs;
    private final String error;
    private final String workflowRunId;
    private final Double elapsedTime;

    public DifyWorkflowRunResult(
        String status,
        Map<String, Object> outputs,
        String error,
        String workflowRunId,
        Double elapsedTime
    ) {
        this.status = status;
        this.outputs = outputs == null ? Map.of() : outputs;
        this.error = error;
        this.workflowRunId = workflowRunId;
        this.elapsedTime = elapsedTime;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public String getError() {
        return error;
    }

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    public Double getElapsedTime() {
        return elapsedTime;
    }
}
