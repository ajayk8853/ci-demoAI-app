package com.pipeline.guardian_ai.dto;

import java.util.List;

public record ComparisonDto(
        Long failedRunId,
        Long successfulRunId,
        Long durationDifferenceSeconds,
        String branchName,
        List<String> differences,
        List<String> observedPatterns
) {}

