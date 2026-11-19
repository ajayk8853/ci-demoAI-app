package com.pipeline.guardian_ai.dto;

import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.entity.PipelineRun;
import java.util.List;

public record DashboardDto(
        long totalRuns,
        long successfulRuns,
        long failedRuns,
        double reliabilityScore,
        long recurringIssues,
        List<PipelineRun> recentRuns,
        List<IssuePattern> issueHistory
) {}

