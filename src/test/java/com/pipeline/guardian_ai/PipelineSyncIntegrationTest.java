package com.pipeline.guardian_ai;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import com.pipeline.guardian_ai.service.GitHubActionsClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PipelineSyncIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean GitHubActionsClient client;

    @Test
    void syncCreatesAndUpdatesTheSameRun() throws Exception {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        var failed = new WorkflowRunDto(-987654321L, "Build", "completed", "failure",
                "main", start, start, start.plusSeconds(60));
        var passed = new WorkflowRunDto(-987654321L, "Build", "completed", "success",
                "main", start, start, start.plusSeconds(30));
        when(client.owner()).thenReturn("owner");
        when(client.repository()).thenReturn("repo");
        when(client.fetchRuns()).thenReturn(List.of(failed)).thenReturn(List.of(passed));

        mvc.perform(post("/api/pipelines/sync"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.synced").value(1));
        mvc.perform(post("/api/pipelines/sync"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.synced").value(1));
        mvc.perform(get("/api/pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.runId == -987654321)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.runId == -987654321)].conclusion").value("success"))
                .andExpect(jsonPath("$[?(@.runId == -987654321)].durationSeconds").value(30));
    }
}
