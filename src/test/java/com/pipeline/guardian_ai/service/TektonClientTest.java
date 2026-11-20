package com.pipeline.guardian_ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class TektonClientTest {
    private static final String RUNS_URI =
            "https://kubernetes.test/apis/tekton.dev/v1/namespaces/ci/pipelineruns";
    private static final String PIPELINE_RUN = """
            {"items":[{"metadata":{"uid":"run-uid","namespace":"ci","name":"build-1",
              "creationTimestamp":"2025-01-01T00:00:00Z"},
              "spec":{"pipelineRef":{"name":"build"}},
              "status":{"conditions":[{"type":"Succeeded","status":"False","reason":"Failed",
                "message":"Task build failed"}],
                "childReferences":[{"kind":"TaskRun","name":"build-task-run"}]}}]}
            """;

    @Test
    void collectsPipelineRunConditionAndTektonStepLogs() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kubernetes.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TektonClient client = new TektonClient(builder.build(), "ci");

        server.expect(requestTo(RUNS_URI))
                .andRespond(withSuccess(PIPELINE_RUN, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kubernetes.test/apis/tekton.dev/v1/namespaces/ci/taskruns/build-task-run"))
                .andRespond(withSuccess("""
                        {"status":{"podName":"build-pod","conditions":[{"type":"Succeeded",
                          "status":"False","message":"Compiler returned non-zero"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kubernetes.test/api/v1/namespaces/ci/pods/build-pod"))
                .andRespond(withSuccess("""
                        {"spec":{"containers":[{"name":"step-build"},{"name":"place-tools"}]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kubernetes.test/api/v1/namespaces/ci/pods/build-pod/log?container=step-build"))
                .andRespond(withSuccess("BUILD FAILURE: cannot find symbol", MediaType.TEXT_PLAIN));

        String evidence = client.fetchRunEvidence(TektonClient.stableId("run-uid"));

        assertThat(evidence).contains("Task build failed", "Compiler returned non-zero",
                "BUILD FAILURE: cannot find symbol");
        server.verify();
    }

    @Test
    void reportsMissingRbacInsteadOfReturningEmptyEvidence() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kubernetes.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TektonClient client = new TektonClient(builder.build(), "ci");
        server.expect(requestTo(RUNS_URI)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> client.fetchRunEvidence(1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        server.verify();
    }
}
