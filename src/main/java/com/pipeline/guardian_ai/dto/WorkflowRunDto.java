package com.pipeline.guardian_ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record WorkflowRunDto(
        Long id,
        String name,
        String status,
        String conclusion,
        @JsonProperty("head_branch") String headBranch,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("run_started_at") Instant runStartedAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}
