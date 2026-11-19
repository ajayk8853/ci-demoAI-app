package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PipelineSyncService {
    private final GitHubActionsClient client;
    private final PipelineRunWriter writer;

    public PipelineSyncService(GitHubActionsClient client, PipelineRunWriter writer) {
        this.client = client;
        this.writer = writer;
    }

    public int sync() {
        List<WorkflowRunDto> runs = client.fetchRuns();
        return writer.saveRuns(runs, client.owner(), client.repository());
    }
}
