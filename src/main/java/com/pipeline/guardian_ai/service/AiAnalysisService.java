package com.pipeline.guardian_ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pipeline.guardian_ai.dto.AnalysisDto;
import com.pipeline.guardian_ai.dto.ComparisonDto;
import com.pipeline.guardian_ai.entity.AnalysisResult;
import com.pipeline.guardian_ai.entity.PipelineRun;
import com.pipeline.guardian_ai.repository.AnalysisResultRepository;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(AiAnalysisService.class);
    private final PipelineRunRepository runs;
    private final AnalysisResultRepository analyses;
    private final GitHubActionsClient github;
    private final TektonClient tekton;
    private final LogArchiveService archives;
    private final LogAnalysisService logAnalysis;
    private final TestReportParserService testReports;
    private final ComparisonService comparison;
    private final IssueTrackerService issues;
    private final ObjectMapper mapper;
    private final String openAiKey;
    private final String provider;

    public AiAnalysisService(PipelineRunRepository runs, AnalysisResultRepository analyses,
                             GitHubActionsClient github, TektonClient tekton, LogArchiveService archives,
                             LogAnalysisService logAnalysis, TestReportParserService testReports, ComparisonService comparison,
                             IssueTrackerService issues, ObjectMapper mapper,
                             @Value("${openai.api-key:}") String openAiKey,
                             @Value("${pipeline.provider:github}") String provider) {
        this.runs = runs;
        this.analyses = analyses;
        this.github = github;
        this.tekton = tekton;
        this.archives = archives;
        this.logAnalysis = logAnalysis;
        this.testReports = testReports;
        this.comparison = comparison;
        this.issues = issues;
        this.mapper = mapper;
        this.openAiKey = openAiKey;
        this.provider = provider.trim().toLowerCase(Locale.ROOT);
    }

    public AnalysisDto analyze(Long runId) {
        PipelineRun failed = runs.findByRunId(runId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline run not found"));
        PipelineRun success = failed.getCreatedAt() == null ? null :
                runs.findTopByConclusionIgnoreCaseAndRepositoryOwnerAndRepositoryNameAndWorkflowNameAndCreatedAtBeforeOrderByCreatedAtDesc(
                        "success", failed.getRepositoryOwner(), failed.getRepositoryName(), failed.getWorkflowName(),
                        failed.getCreatedAt()).orElse(null);

        RunEvidence failedEvidence = fetchEvidence(runId);
        RunEvidence successfulEvidence = null;
        if (success != null) {
            try {
                successfulEvidence = fetchEvidence(success.getRunId());
            } catch (ResponseStatusException | IllegalArgumentException ex) {
                String reason = ex instanceof ResponseStatusException status
                        ? status.getReason() : ex.getMessage();
                logger.warn("Could not retrieve evidence for previous successful run {}: {}",
                        success.getRunId(), reason);
            }
        }
        ComparisonDto comparisonDto = comparison.compare(failed, success, failedEvidence.patterns(),
                successfulEvidence == null ? null : successfulEvidence.patterns());
        List<String> evidenceLines = new ArrayList<>();
        if (!failedEvidence.patterns().isEmpty()) {
            evidenceLines.add("Matched log patterns: " + String.join(", ", failedEvidence.patterns()));
        }
        evidenceLines.addAll(failedEvidence.reportFindings());
        if (!failedEvidence.log().isBlank() && isTektonProvider()) evidenceLines.add(failedEvidence.log());

        String rootCause;
        String evidence;
        String recommendation;
        double confidence;
        String source;
        if (!openAiKey.isBlank()) {
            AiResult ai = askOpenAi(failed, success, failedEvidence, successfulEvidence);
            rootCause = ai.rootCause();
            evidence = String.join("\n", appendEvidence(ai.evidence(), failedEvidence.reportFindings()));
            recommendation = ai.recommendation();
            confidence = ai.confidence(); source = "OpenAI";
        } else {
            rootCause = failedEvidence.patterns().isEmpty() ? "No known error pattern detected"
                    : failedEvidence.patterns().getFirst() + " detected";
            if (evidenceLines.isEmpty()) {
                evidenceLines.add("No matching error signature was found in the pipeline evidence");
            }
            evidence = String.join("\n", evidenceLines);
            recommendation = recommendation(rootCause);
            confidence = failedEvidence.patterns().isEmpty() ? 35
                    : 70 + Math.min(failedEvidence.patterns().size() * 5, 20);
            source = "local-rule-engine";
        }

        String issueMessage = failedEvidence.log().isBlank() ? rootCause : primaryError(failedEvidence.log());
        var tracked = issues.track(runId, redactSensitiveValues(issueMessage), rootCause);
        evidence = redactSensitiveValues(evidence);
        AnalysisResult saved = AnalysisResult.builder()
                .runId(runId).rootCause(rootCause).confidence(confidence)
                .evidence(evidence).recommendation(recommendation).source(source)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC)).build();
        analyses.save(saved);

        return new AnalysisDto(runId, rootCause, confidence,
                evidence.lines().limit(10).map(String::trim).filter(v -> !v.isBlank()).toList(),
                recommendation, source, comparisonDto, tracked.recurring(), tracked.previousOccurrences());
    }

    private String primaryError(String log) {
        return log.lines().filter(line -> line.toLowerCase(Locale.ROOT)
                        .matches(".*(error|failed|failure|exception|denied|timeout).*"))
                .limit(3).reduce((a, b) -> a + " | " + b).orElse("Unknown pipeline failure");
    }

    private RunEvidence fetchEvidence(Long runId) {
        String log;
        List<String> reportFindings;
        if (isTektonProvider()) {
            log = tekton.fetchRunEvidence(runId);
            reportFindings = List.of();
        } else {
            byte[] archive = github.downloadLogs(runId);
            log = archives.extractText(archive);
            reportFindings = archives.extractTestReports(archive).stream()
                    .flatMap(report -> testReports.analyze(report).stream()).toList();
        }
        List<String> patterns = new ArrayList<>(logAnalysis.analyze(log));
        if (!reportFindings.isEmpty() && !patterns.contains("Test Failure")) patterns.add("Test Failure");
        return new RunEvidence(log, List.copyOf(patterns), reportFindings);
    }

    private boolean isTektonProvider() {
        return "tekton".equals(provider) || "kubernetes".equals(provider);
    }

    private String recommendation(String rootCause) {
        String lower = rootCause.toLowerCase(Locale.ROOT);
        if (lower.contains("memory")) return "Increase runner memory or reduce build/test memory usage and retry.";
        if (lower.contains("network")) return "Verify service DNS, network access, ports, and retry health checks before build steps.";
        if (lower.contains("security") || lower.contains("permission")) return "Review GitHub permissions, secrets, file ownership, and least-privilege policies.";
        if (lower.contains("dependency") || lower.contains("package")) return "Pin dependency versions, clear stale caches, and use a lockfile.";
        if (lower.contains("docker")) return "Validate the Dockerfile, base image, registry credentials, and build context.";
        if (lower.contains("test")) return "Review the failing test report and rerun only the affected test suite.";
        if (lower.contains("compilation")) return "Resolve the compiler/type error shown in the log and add a pre-merge build check.";
        return "Inspect the downloaded log, reproduce the failure locally, and add a targeted CI diagnostic.";
    }

    private List<String> appendEvidence(String evidence, List<String> reportFindings) {
        List<String> combined = new ArrayList<>();
        if (evidence != null && !evidence.isBlank()) combined.add(evidence);
        combined.addAll(reportFindings);
        return combined;
    }

    private AiResult askOpenAi(PipelineRun failed, PipelineRun success, RunEvidence failedEvidence,
                               RunEvidence successfulEvidence) {
        try {
            ObjectNode user = mapper.createObjectNode();
            user.put("failedRunId", failed.getRunId());
            user.put("successfulRunId", success == null ? null : success.getRunId());
            user.put("branch", failed.getBranchName());
            user.set("patterns", mapper.valueToTree(failedEvidence.patterns()));
            user.set("testReportFindings", mapper.valueToTree(failedEvidence.reportFindings()));
            user.put("logSummary", summarize(failedEvidence.log()));
            user.set("successfulPatterns", mapper.valueToTree(
                    successfulEvidence == null ? List.of() : successfulEvidence.patterns()));
            user.set("successfulTestReportFindings", mapper.valueToTree(
                    successfulEvidence == null ? List.of() : successfulEvidence.reportFindings()));
            user.put("successfulLogSummary", successfulEvidence == null ? "" : summarize(successfulEvidence.log()));

            ObjectNode request = mapper.createObjectNode();
            request.put("model", "gpt-4o-mini");
            request.put("temperature", 0.1);
            ArrayNode messages = request.putArray("messages");
            messages.add(mapper.createObjectNode().put("role", "system").put("content",
                    "You are an expert Site Reliability Engineer. Analyze the supplied CI/CD evidence. Return only valid JSON with rootCause, confidence (0-100), evidence, recommendation."));
            messages.add(mapper.createObjectNode().put("role", "user").put("content", user.toString()));

            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(60));
            JsonNode response = RestClient.builder().baseUrl("https://api.openai.com/v1")
                    .requestFactory(requestFactory)
                    .defaultHeader("Authorization", "Bearer " + openAiKey)
                    .build().post().uri("/chat/completions").body(request)
                    .retrieve().body(JsonNode.class);
            String content = response == null ? "" :
                    response.path("choices").path(0).path("message").path("content").asText();
            JsonNode ai = mapper.readTree(content);
            if (ai == null || !ai.isObject() || ai.path("rootCause").asText().isBlank()) {
                throw JsonMappingException.fromUnexpectedIOE(
                        new java.io.IOException("OpenAI returned an incomplete analysis response"));
            }
            double confidence = ai.path("confidence").asDouble(70);
            if (!Double.isFinite(confidence)) confidence = 70;
            if (confidence < 0) confidence = 0;
            if (confidence > 100) confidence = 100;
            return new AiResult(ai.path("rootCause").asText("Unknown root cause"),
                    ai.path("evidence").asText("No evidence returned"),
                    ai.path("recommendation").asText("Review the CI logs"), confidence);
        } catch (RestClientException | JsonProcessingException ex) {
            logger.warn("OpenAI analysis failed; using local pattern analysis: {}", ex.getMessage());
            List<String> patterns = failedEvidence.patterns();
            String root = patterns.isEmpty() ? "No known error pattern detected" : patterns.getFirst() + " detected";
            return new AiResult(root, "OpenAI request failed; pattern analysis was used",
                    recommendation(root), patterns.isEmpty() ? 35 : 70);
        }
    }

    private String summarize(String log) {
        String redacted = redactSensitiveValues(log);
        return redacted.length() > 12000 ? redacted.substring(0, 12000) : redacted;
    }

    static String redactSensitiveValues(String text) {
        return text
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1[REDACTED]")
                .replaceAll("(?i)\\b(password|passwd|secret|token|api[_-]?key)(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)",
                        "$1$2[REDACTED]")
                .replaceAll("\\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b", "[REDACTED]")
                .replaceAll("\\bAKIA[0-9A-Z]{16}\\b", "[REDACTED]")
                .replaceAll("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@", "$1[REDACTED]@")
                .replaceAll("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----",
                        "[REDACTED PRIVATE KEY]");
    }

    private record RunEvidence(String log, List<String> patterns, List<String> reportFindings) {}

    private record AiResult(String rootCause, String evidence, String recommendation, double confidence) {}
}
