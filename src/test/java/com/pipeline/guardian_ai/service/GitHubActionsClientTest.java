package com.pipeline.guardian_ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GitHubActionsClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubActionsClient client = new GitHubActionsClient(builder.build(), "secret", "owner", "repo");

    @Test
    void fetchesEveryPageAndMapsFields() {
        String firstPage = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "{\"id\":" + i + ",\"status\":\"completed\",\"created_at\":\"2025-01-01T00:00:00Z\"}")
                .collect(Collectors.joining(","));
        server.expect(once(), requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess("{\"workflow_runs\":[" + firstPage + "]}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=100&page=2"))
                .andRespond(withSuccess("""
                        {"workflow_runs":[{"id":101,"name":"Build","status":"completed",
                          "conclusion":"failure","head_branch":"main",
                          "created_at":"2025-01-01T00:00:00Z"}]}
                        """, MediaType.APPLICATION_JSON));

        var runs = client.fetchRuns();
        assertEquals(101, runs.size());
        assertEquals("failure", runs.get(100).conclusion());
        assertEquals("main", runs.get(100).headBranch());
        server.verify();
    }

    @Test
    void reportsGithubFailure() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, client::fetchRuns).getStatusCode());
    }

    @Test
    void rejectsIncompleteResponses() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/actions/runs?per_page=100&page=1"))
                .andRespond(withSuccess("{\"workflow_runs\":[{\"status\":\"completed\"}]}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(ResponseStatusException.class, client::fetchRuns).getStatusCode());
    }

    @Test
    void requiresRepositoryConfiguration() {
        var unconfigured = new GitHubActionsClient(builder.build(), "", "", "");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, unconfigured::fetchRuns).getStatusCode());
    }
}
