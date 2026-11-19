package com.pipeline.guardian_ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WorkflowRunsResponse(@JsonProperty("workflow_runs") List<WorkflowRunDto> workflowRuns) {}
