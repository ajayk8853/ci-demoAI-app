package com.pipeline.guardian_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TektonClient {
    private static final int MAX_TASK_RUNS = 10;
    private static final int MAX_STEP_CONTAINERS = 5;
    private static final int MAX_EVIDENCE_CHARACTERS = 250_000;
    private final RestClient client;
    private final String namespace;

    @Autowired
    public TektonClient(
            @Value("${tekton.api-url:}") String apiUrl,
            @Value("${tekton.namespace:}") String namespace,
            @Value("${tekton.token:}") String token,
            @Value("${tekton.insecure-skip-tls-verify:false}") boolean insecure) {
        this(createClient(apiUrl, token, insecure), namespace);
    }

    TektonClient(RestClient client, String namespace) {
        this.namespace = namespace == null ? "" : namespace.trim();
        this.client = client;
    }

    private static RestClient createClient(String apiUrl, String token, boolean insecure) {
        if (apiUrl == null || apiUrl.isBlank()) {
            String host = System.getenv("KUBERNETES_SERVICE_HOST");
            String port = System.getenv("KUBERNETES_SERVICE_PORT_HTTPS");
            apiUrl = host == null ? "" : "https://" + host + ":" + (port == null ? "443" : port);
        }
        if (token == null || token.isBlank()) {
            Path tokenFile = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
            try {
                if (Files.exists(tokenFile)) token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            } catch (Exception ignored) {
            }
        }

        HttpClient.Builder httpBuilder = HttpClient.newBuilder();
        httpBuilder.connectTimeout(Duration.ofSeconds(10));
        if (insecure) {
            httpBuilder.sslContext(trustAllSslContext());
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpBuilder.build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (!apiUrl.isBlank()) builder.baseUrl(apiUrl.replaceAll("/$", ""));
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder.build();
    }

    public List<WorkflowRunDto> fetchRuns() {
        if (namespace.isBlank() && System.getenv("KUBERNETES_SERVICE_HOST") == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure TEKTON_API_URL and TEKTON_NAMESPACE before syncing Tekton runs");
        }

        String uri = namespace.isBlank()
                ? "/apis/tekton.dev/v1/pipelineruns"
                : "/apis/tekton.dev/v1/namespaces/{namespace}/pipelineruns";

        try {
            JsonNode response = namespace.isBlank()
                    ? client.get().uri(uri).retrieve().body(JsonNode.class)
                    : client.get().uri(uri, namespace).retrieve().body(JsonNode.class);
            if (response == null || !response.has("items") || !response.path("items").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Kubernetes returned an invalid Tekton PipelineRun response");
            }

            List<WorkflowRunDto> runs = new ArrayList<>();
            for (JsonNode item : response.path("items")) {
                JsonNode metadata = item.path("metadata");
                JsonNode spec = item.path("spec");
                JsonNode status = item.path("status");

                String uid = text(metadata, "uid");
                String name = text(metadata, "name");
                if (uid.isBlank()) uid = text(metadata, "namespace") + "/" + name;
                if (name.isBlank()) continue;

                String pipelineName = text(spec.path("pipelineRef"), "name");
                if (pipelineName.isBlank()) pipelineName = name;

                JsonNode labels = metadata.path("labels");
                String branch = text(labels, "tekton.dev/gitBranch");

                JsonNode conditions = status.path("conditions");
                JsonNode succeeded = firstCondition(conditions);

                String conditionStatus = succeeded == null ? "" : text(succeeded, "status");
                String reason = succeeded == null ? "" : text(succeeded, "reason");

                String runStatus;
                String conclusion;
                if (conditionStatus.equalsIgnoreCase("True") || "Succeeded".equalsIgnoreCase(reason)) {
                    runStatus = "completed"; conclusion = "success";
                } else if (conditionStatus.equalsIgnoreCase("False") || "Failed".equalsIgnoreCase(reason)) {
                    runStatus = "completed"; conclusion = "failure";
                } else if ("Cancelled".equalsIgnoreCase(reason) || "Canceled".equalsIgnoreCase(reason)) {
                    runStatus = "completed"; conclusion = "cancelled";
                } else {
                    runStatus = "in_progress"; conclusion = null;
                }

                Instant created = parseInstant(text(metadata, "creationTimestamp"));
                Instant start = parseInstant(text(status, "startTime"));
                Instant updated = parseInstant(text(status, "completionTime"));

                runs.add(new WorkflowRunDto(stableId(uid), pipelineName, runStatus, conclusion,
                        branch, created, start, updated));
            }
            return runs;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Kubernetes returned HTTP " + ex.getStatusCode().value()
                            + " while retrieving Tekton PipelineRuns; check RBAC and Tekton installation", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not retrieve Tekton PipelineRuns from Kubernetes", ex);
        }
    }

    public String fetchRunEvidence(Long runId) {
        try {
            JsonNode response = getPipelineRuns();
            if (response == null || !response.path("items").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Kubernetes returned an invalid Tekton PipelineRun response");
            }
            for (JsonNode item : response.path("items")) {
                JsonNode metadata = item.path("metadata");
                String uid = text(metadata, "uid");
                if (uid.isBlank()) uid = text(metadata, "namespace") + "/" + text(metadata, "name");
                if (stableId(uid) == runId) return collectEvidence(item);
            }
            return "Tekton PipelineRun was not found; it may have been deleted from the cluster";
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Kubernetes returned HTTP " + ex.getStatusCode().value()
                            + " while retrieving Tekton analysis evidence; check RBAC permissions", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not retrieve Tekton analysis evidence from Kubernetes", ex);
        }
    }

    private JsonNode getPipelineRuns() {
        String uri = namespace.isBlank()
                ? "/apis/tekton.dev/v1/pipelineruns"
                : "/apis/tekton.dev/v1/namespaces/{namespace}/pipelineruns";
        return namespace.isBlank()
                ? client.get().uri(uri).retrieve().body(JsonNode.class)
                : client.get().uri(uri, namespace).retrieve().body(JsonNode.class);
    }

    private String collectEvidence(JsonNode pipelineRun) {
        JsonNode metadata = pipelineRun.path("metadata");
        String runNamespace = text(metadata, "namespace");
        if (runNamespace.isBlank()) runNamespace = namespace;

        JsonNode succeeded = firstCondition(pipelineRun.path("status").path("conditions"));
        String reason = succeeded == null ? "" : text(succeeded, "reason");
        String message = succeeded == null ? "" : text(succeeded, "message");
        List<String> evidence = new ArrayList<>();
        addEvidence(evidence, succeeded == null ? "Tekton PipelineRun has no Succeeded condition"
                : "Tekton Succeeded condition: " + text(succeeded, "status")
                        + (reason.isBlank() ? "" : " reason=" + reason)
                        + (message.isBlank() ? "" : " message=" + message));

        JsonNode taskRuns = pipelineRun.path("status").path("childReferences");
        if (taskRuns.isArray() && !runNamespace.isBlank()) {
            int processed = 0;
            for (JsonNode task : taskRuns) {
                String taskName = text(task, "name");
                String kind = text(task, "kind");
                if (taskName.isBlank() || (!kind.isBlank() && !"TaskRun".equalsIgnoreCase(kind))) continue;
                if (processed++ >= MAX_TASK_RUNS) {
                    addEvidence(evidence, "Additional TaskRun logs omitted after the " + MAX_TASK_RUNS + "-task limit");
                    break;
                }
                collectTaskEvidence(runNamespace, taskName, evidence);
            }
        }
        return String.join("\n", evidence);
    }

    private void collectTaskEvidence(String runNamespace, String taskName, List<String> evidence) {
        JsonNode taskRun = client.get()
                .uri("/apis/tekton.dev/v1/namespaces/{namespace}/taskruns/{name}", runNamespace, taskName)
                .retrieve().body(JsonNode.class);
        if (taskRun == null) {
            evidence.add("TaskRun " + taskName + " returned an empty response");
            return;
        }

        JsonNode taskCondition = firstCondition(taskRun.path("status").path("conditions"));
        String taskMessage = taskCondition == null ? "" : text(taskCondition, "message");
        if (!taskMessage.isBlank()) addEvidence(evidence, "TaskRun " + taskName + ": " + taskMessage);

        String podName = text(taskRun.path("status"), "podName");
        if (podName.isBlank()) return;
        JsonNode pod = client.get()
                .uri("/api/v1/namespaces/{namespace}/pods/{name}", runNamespace, podName)
                .retrieve().body(JsonNode.class);
        if (pod == null || !pod.path("spec").path("containers").isArray()) return;

        int containers = 0;
        for (JsonNode container : pod.path("spec").path("containers")) {
            String containerName = text(container, "name");
            if (!containerName.startsWith("step-")) continue;
            if (containers++ >= MAX_STEP_CONTAINERS) {
                addEvidence(evidence, "Additional step logs omitted after the " + MAX_STEP_CONTAINERS
                        + "-container limit for TaskRun " + taskName);
                break;
            }
            String log = client.get()
                    .uri("/api/v1/namespaces/{namespace}/pods/{pod}/log?container={container}",
                            runNamespace, podName, containerName)
                    .retrieve().body(String.class);
            if (log != null && !log.isBlank()) {
                String boundedLog = log.length() > 50_000 ? log.substring(0, 50_000) : log;
                addEvidence(evidence, "TaskRun " + taskName + " / " + containerName + ":\n" + boundedLog);
            }
        }
    }

    private static void addEvidence(List<String> evidence, String item) {
        int used = evidence.stream().mapToInt(String::length).sum();
        int remaining = MAX_EVIDENCE_CHARACTERS - used;
        if (remaining <= 0) return;
        evidence.add(item.length() > remaining ? item.substring(0, remaining) : item);
    }

    private static JsonNode firstCondition(JsonNode conditions) {
        if (conditions == null || !conditions.isArray()) return null;
        for (JsonNode condition : conditions) {
            if ("Succeeded".equalsIgnoreCase(text(condition, "type"))) return condition;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static Instant parseInstant(String value) {
        return value.isBlank() ? null : Instant.parse(value);
    }

    static long stableId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash == Long.MIN_VALUE ? 1 : Math.abs(hash);
    }

    private static SSLContext trustAllSslContext() {
        try {
            TrustManager[] managers = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers, new SecureRandom());
            return context;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not configure Kubernetes TLS", ex);
        }
    }
}
