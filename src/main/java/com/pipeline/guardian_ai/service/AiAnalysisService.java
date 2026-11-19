package com.pipeline.guardian_ai.service;

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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiAnalysisService {
    private final PipelineRunRepository runs;
    private final AnalysisResultRepository analyses;
    private final GitHubActionsClient github;
    private final LogArchiveService archives;
    private final LogAnalysisService logAnalysis;
    private final ComparisonService comparison;
    private final IssueTrackerService issues;
    private final ObjectMapper mapper;
    private final String openAiKey;

    public AiAnalysisService(PipelineRunRepository runs, AnalysisResultRepository analyses,
                             GitHubActionsClient github, LogArchiveService archives,
                             LogAnalysisService logAnalysis, ComparisonService comparison,
                             IssueTrackerService issues, ObjectMapper mapper,
                             @Value("${openai.api-key:}") String openAiKey) {
        this.runs = runs;
        this.analyses = analyses;
        this.github = github;
        this.archives = archives;
        this.logAnalysis = logAnalysis;
        this.comparison = comparison;
        this.issues = issues;
        this.mapper = mapper;
        this.openAiKey = openAiKey;
    }

    public AnalysisDto analyze(Long runId) {
        PipelineRun failed = runs.findByRunId(runId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline run not found"));
        PipelineRun success = failed.getCreatedAt() == null ? null :
                runs.findTopByConclusionIgnoreCaseAndCreatedAtBeforeOrderByCreatedAtDesc("success", failed.getCreatedAt()).orElse(null);

        String log = archives.extractText(github.downloadLogs(runId));
        List<String> patterns = logAnalysis.analyze(log);
        ComparisonDto comparisonDto = comparison.compare(failed, success, patterns);

        String rootCause;
        String evidence;
        String recommendation;
        double confidence;
        String source;
        if (!openAiKey.isBlank()) {
            AiResult ai = askOpenAi(failed, success, log, patterns);
            rootCause = ai.rootCause(); evidence = ai.evidence(); recommendation = ai.recommendation();
            confidence = ai.confidence(); source = "OpenAI";
        } else {
            rootCause = patterns.isEmpty() ? "No known error pattern detected" : patterns.getFirst() + " detected";
            evidence = patterns.isEmpty() ? "No matching error signature was found in the downloaded logs"
                    : "Matched log patterns: " + String.join(", ", patterns);
            recommendation = recommendation(rootCause);
            confidence = patterns.isEmpty() ? 35 : 70 + Math.min(patterns.size() * 5, 20);
            source = "local-rule-engine";
        }

        var tracked = issues.track(log.isBlank() ? rootCause : primaryError(log), rootCause);
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
        return log.lines().filter(line -> line.toLowerCase().matches(".*(error|failed|failure|exception|denied|timeout).*"))
                .limit(3).reduce((a, b) -> a + " | " + b).orElse("Unknown pipeline failure");
    }

    private String recommendation(String rootCause) {
        String lower = rootCause.toLowerCase();
        if (lower.contains("memory")) return "Increase runner memory or reduce build/test memory usage and retry.";
        if (lower.contains("network")) return "Verify service DNS, network access, ports, and retry health checks before build steps.";
        if (lower.contains("security") || lower.contains("permission")) return "Review GitHub permissions, secrets, file ownership, and least-privilege policies.";
        if (lower.contains("dependency") || lower.contains("package")) return "Pin dependency versions, clear stale caches, and use a lockfile.";
        if (lower.contains("docker")) return "Validate the Dockerfile, base image, registry credentials, and build context.";
        if (lower.contains("test")) return "Review the failing test report and rerun only the affected test suite.";
        if (lower.contains("compilation")) return "Resolve the compiler/type error shown in the log and add a pre-merge build check.";
        return "Inspect the downloaded log, reproduce the failure locally, and add a targeted CI diagnostic.";
    }

    private AiResult askOpenAi(PipelineRun failed, PipelineRun success, String log, List<String> patterns) {
        try {
            ObjectNode user = mapper.createObjectNode();
            user.put("failedRunId", failed.getRunId());
            user.put("successfulRunId", success == null ? null : success.getRunId());
            user.put("branch", failed.getBranchName());
            user.set("patterns", mapper.valueToTree(patterns));
            user.put("logSummary", log.length() > 12000 ? log.substring(0, 12000) : log);

            ObjectNode request = mapper.createObjectNode();
            request.put("model", "gpt-4o-mini");
            request.put("temperature", 0.1);
            ArrayNode messages = request.putArray("messages");
            messages.add(mapper.createObjectNode().put("role", "system").put("content",
                    "You are an expert Site Reliability Engineer. Analyze the supplied CI/CD evidence. Return only valid JSON with rootCause, confidence (0-100), evidence, recommendation."));
            messages.add(mapper.createObjectNode().put("role", "user").put("content", user.toString()));

            JsonNode response = RestClient.builder().baseUrl("https://api.openai.com/v1")
                    .defaultHeader("Authorization", "Bearer " + openAiKey)
                    .build().post().uri("/chat/completions").body(request)
                    .retrieve().body(JsonNode.class);
            String content = response.path("choices").path(0).path("message").path("content").asText();
            JsonNode ai = mapper.readTree(content);
            double confidence = ai.path("confidence").asDouble(70);
            if (confidence < 0) confidence = 0; if (confidence > 100) confidence = 100;
            return new AiResult(ai.path("rootCause").asText("Unknown root cause"),
                    ai.path("evidence").asText("No evidence returned"),
                    ai.path("recommendation").asText("Review the CI logs"), confidence);
        } catch (Exception ex) {
            String root = patterns.isEmpty() ? "No known error pattern detected" : patterns.getFirst() + " detected";
            return new AiResult(root, "OpenAI request failed; pattern analysis was used",
                    recommendation(root), patterns.isEmpty() ? 35 : 70);
        }
    }

    private record AiResult(String rootCause, String evidence, String recommendation, double confidence) {}
}
