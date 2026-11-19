package com.pipeline.guardian_ai.dto;

public record ReliabilityDto(
        long totalRuns,
        long successfulRuns,
        long failedRuns,
        double reliabilityScore
) {}

