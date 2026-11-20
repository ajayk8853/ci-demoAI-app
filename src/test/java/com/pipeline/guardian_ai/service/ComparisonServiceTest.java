package com.pipeline.guardian_ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pipeline.guardian_ai.entity.PipelineRun;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {
    @Test
    void comparesFailureWithPreviousSuccess() {
        PipelineRun failed = run(20L, "failure", "feature/build", 90L);
        PipelineRun successful = run(19L, "success", "main", 45L);

        var comparison = new ComparisonService().compare(failed, successful, List.of("Test Failure"), List.of());

        assertThat(comparison.successfulRunId()).isEqualTo(19L);
        assertThat(comparison.durationDifferenceSeconds()).isEqualTo(45L);
        assertThat(comparison.differences()).contains("Branch changed between runs", "Build duration changed",
                "Failure-only pattern: Test Failure");
        assertThat(comparison.observedPatterns()).containsExactly("Test Failure");
    }

    private PipelineRun run(Long id, String conclusion, String branch, Long duration) {
        PipelineRun run = new PipelineRun();
        run.setRunId(id);
        run.setConclusion(conclusion);
        run.setBranchName(branch);
        run.setDurationSeconds(duration);
        return run;
    }
}
