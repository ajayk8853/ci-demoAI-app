package com.pipeline.guardian_ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.guardian_ai.entity.PipelineRun;
import com.pipeline.guardian_ai.repository.AnalysisResultRepository;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiAnalysisServiceTest {
    private final PipelineRunRepository runs = mock(PipelineRunRepository.class);
    private final AnalysisResultRepository analyses = mock(AnalysisResultRepository.class);
    private final GitHubActionsClient github = mock(GitHubActionsClient.class);
    private final TektonClient tekton = mock(TektonClient.class);
    private final LogArchiveService archives = mock(LogArchiveService.class);
    private final IssueTrackerService issues = mock(IssueTrackerService.class);
    private final TestReportParserService reports = new TestReportParserService();
    private final String junit = """
            <testsuite name="unit" failures="1">
              <testcase classname="BuildTest" name="fails">
                <failure message="assertion mismatch"/>
              </testcase>
            </testsuite>
            """;

    @Test
    void includesJUnitFailureDetailsInGitHubAnalysis() {
        PipelineRun failed = failedRun();
        when(runs.findByRunId(42L)).thenReturn(Optional.of(failed));
        when(runs.findTopByConclusionIgnoreCaseAndRepositoryOwnerAndRepositoryNameAndWorkflowNameAndCreatedAtBeforeOrderByCreatedAtDesc(
                "success", "owner", "repo", "build", failed.getCreatedAt())).thenReturn(Optional.empty());
        when(github.downloadLogs(42L)).thenReturn(new byte[] {1});
        when(archives.extractText(any())).thenReturn("BUILD FAILURE");
        when(archives.extractTestReports(any())).thenReturn(List.of(junit));
        when(issues.track(eq(42L), any(), any())).thenReturn(new IssueTrackerService.RecurringIssue(false, 0));

        var result = service("github").analyze(42L);

        assertThat(result.rootCause()).isEqualTo("Test Failure detected");
        assertThat(result.evidence()).anySatisfy(line -> assertThat(line).contains("BuildTest.fails"));
        assertThat(result.comparison().successfulRunId()).isNull();
        verify(github).downloadLogs(42L);
        verifyNoInteractions(tekton);
    }

    @Test
    void usesTektonStepEvidenceWithoutCallingGitHub() {
        PipelineRun failed = failedRun();
        when(runs.findByRunId(42L)).thenReturn(Optional.of(failed));
        when(runs.findTopByConclusionIgnoreCaseAndRepositoryOwnerAndRepositoryNameAndWorkflowNameAndCreatedAtBeforeOrderByCreatedAtDesc(
                "success", "owner", "repo", "build", failed.getCreatedAt())).thenReturn(Optional.empty());
        when(tekton.fetchRunEvidence(42L)).thenReturn("BUILD FAILURE: cannot find symbol");
        when(issues.track(eq(42L), any(), any())).thenReturn(new IssueTrackerService.RecurringIssue(false, 0));

        var result = service("tekton").analyze(42L);

        assertThat(result.rootCause()).isEqualTo("Compilation Failure detected");
        assertThat(result.evidence()).anySatisfy(line -> assertThat(line).contains("cannot find symbol"));
        verify(tekton).fetchRunEvidence(42L);
        verifyNoInteractions(github);
        verifyNoInteractions(archives);
    }

    @Test
    void comparesFailurePatternsWithThePreviousSuccessfulRun() {
        PipelineRun failed = failedRun();
        PipelineRun successful = failedRun();
        successful.setRunId(41L);
        successful.setConclusion("success");
        successful.setCreatedAt(failed.getCreatedAt().minusDays(1));
        when(runs.findByRunId(42L)).thenReturn(Optional.of(failed));
        when(runs.findTopByConclusionIgnoreCaseAndRepositoryOwnerAndRepositoryNameAndWorkflowNameAndCreatedAtBeforeOrderByCreatedAtDesc(
                "success", "owner", "repo", "build", failed.getCreatedAt())).thenReturn(Optional.of(successful));
        when(github.downloadLogs(anyLong())).thenAnswer(invocation ->
                new byte[] {(byte) (long) invocation.getArgument(0)});
        when(archives.extractText(any())).thenAnswer(invocation ->
                ((byte[]) invocation.getArgument(0))[0] == 42 ? "AssertionError: expected value" : "all tests passed");
        when(archives.extractTestReports(any())).thenReturn(List.of());
        when(issues.track(eq(42L), any(), any())).thenReturn(new IssueTrackerService.RecurringIssue(false, 0));

        var result = service("github").analyze(42L);

        assertThat(result.comparison().successfulRunId()).isEqualTo(41L);
        assertThat(result.comparison().differences()).contains("Failure-only pattern: Test Failure");
        verify(github).downloadLogs(42L);
        verify(github).downloadLogs(41L);
    }

    @Test
    void redactsCommonCredentialsBeforeSendingLogEvidenceToOpenAi() {
        String sanitized = AiAnalysisService.redactSensitiveValues(
                "Authorization: Bearer abc123 token='quoted credential' "
                        + "AWS=AKIA1234567890ABCDEF https://user:pass@example.com");

        assertThat(sanitized).doesNotContain("abc123", "quoted credential", "AKIA1234567890ABCDEF",
                "user:pass");
        assertThat(sanitized).contains("[REDACTED]");
    }

    private AiAnalysisService service(String provider) {
        return new AiAnalysisService(runs, analyses, github, tekton, archives, new LogAnalysisService(),
                reports, new ComparisonService(), issues, new ObjectMapper(), "", provider);
    }

    private PipelineRun failedRun() {
        PipelineRun run = new PipelineRun();
        run.setRunId(42L);
        run.setWorkflowName("build");
        run.setRepositoryOwner("owner");
        run.setRepositoryName("repo");
        run.setConclusion("failure");
        run.setCreatedAt(LocalDateTime.parse("2025-01-02T00:00:00"));
        return run;
    }
}
