package com.pipeline.guardian_ai.dto;

import java.util.List;

public record AnalysisDto(
        Long runId,
        String rootCause,
        double confidence,
        List<String> evidence,
        String recommendation,
        String source,
        ComparisonDto comparison,
        boolean recurringIssue,
        int previousOccurrences
) {}

