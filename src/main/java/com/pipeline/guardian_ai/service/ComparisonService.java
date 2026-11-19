package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.ComparisonDto;
import com.pipeline.guardian_ai.entity.PipelineRun;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {
    public ComparisonDto compare(PipelineRun failed, PipelineRun success, List<String> patterns) {
        List<String> differences = new ArrayList<>();
        if (success == null) {
            differences.add("No earlier successful run is available");
        } else {
            if (!safe(failed.getBranchName()).equals(safe(success.getBranchName()))) {
                differences.add("Branch changed between runs");
            }
            if (failed.getDurationSeconds() != null && success.getDurationSeconds() != null
                    && !failed.getDurationSeconds().equals(success.getDurationSeconds())) {
                differences.add("Build duration changed");
            }
        }
        Long durationDifference = null;
        if (failed.getDurationSeconds() != null && success != null && success.getDurationSeconds() != null) {
            durationDifference = failed.getDurationSeconds() - success.getDurationSeconds();
        }
        return new ComparisonDto(failed.getRunId(), success == null ? null : success.getRunId(),
                durationDifference, failed.getBranchName(), differences, patterns);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

