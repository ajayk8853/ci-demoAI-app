package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import com.pipeline.guardian_ai.dto.WorkflowRunsResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GitHubActionsClient {
    private static final int PAGE_SIZE = 100;
    private final RestClient client;
    private final String token;
    private final String owner;
    private final String repository;

    public GitHubActionsClient(RestClient githubRestClient,
                               @Value("${github.token:}") String token,
                               @Value("${github.owner:}") String owner,
                               @Value("${github.repository:}") String repository) {
        this.client = githubRestClient;
        this.token = token;
        this.owner = owner;
        this.repository = repository;
    }

    public String owner() {
        return owner;
    }

    public String repository() {
        return repository;
    }

    public List<WorkflowRunDto> fetchRuns() {
        if (owner.isBlank() || repository.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure GITHUB_OWNER and GITHUB_REPOSITORY before syncing");
        }

        List<WorkflowRunDto> runs = new ArrayList<>();
        for (int page = 1; ; page++) {
            WorkflowRunsResponse response;
            try {
                response = client.get()
                        .uri("/repos/{owner}/{repo}/actions/runs?per_page={perPage}&page={page}",
                                owner, repository, PAGE_SIZE, page)
                        .headers(headers -> {
                            if (!token.isBlank()) {
                                headers.setBearerAuth(token);
                            }
                        })
                        .retrieve()
                        .body(WorkflowRunsResponse.class);
            } catch (RestClientResponseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "GitHub returned HTTP " + ex.getStatusCode().value()
                                + "; check repository access, token permissions, and rate limits", ex);
            } catch (RestClientException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not retrieve workflow runs from GitHub", ex);
            }
            if (response == null || response.workflowRuns() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "GitHub returned an invalid workflow runs response");
            }
            for (WorkflowRunDto run : response.workflowRuns()) {
                if (run == null || run.id() == null || run.status() == null || run.createdAt() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "GitHub returned an incomplete workflow run");
                }
                runs.add(run);
            }
            if (response.workflowRuns().size() < PAGE_SIZE) {
                return runs;
            }
        }
    }

    public byte[] downloadLogs(Long runId) {
        if (owner.isBlank() || repository.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure GITHUB_OWNER and GITHUB_REPOSITORY before downloading logs");
        }
        String location = "/repos/{owner}/{repo}/actions/runs/{runId}/logs";
        try {
            ResponseEntity<byte[]> response = client.get()
                    .uri(location, owner, repository, runId)
                    .headers(headers -> {
                        if (!token.isBlank()) headers.setBearerAuth(token);
                    })
                    .retrieve()
                    .toEntity(byte[].class);
            if (response.getHeaders().getLocation() != null) {
                ResponseEntity<byte[]> redirected = client.get()
                        .uri(response.getHeaders().getLocation())
                        .retrieve().toEntity(byte[].class);
                return redirected.getBody() == null ? new byte[0] : redirected.getBody();
            }
            return response.getBody() == null ? new byte[0] : response.getBody();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "GitHub returned HTTP " + ex.getStatusCode().value()
                            + " while downloading logs", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not download workflow logs", ex);
        }
    }
}
