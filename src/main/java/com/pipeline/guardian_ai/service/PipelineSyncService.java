package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PipelineSyncService {
    private final GitHubActionsClient githubClient;
    private final TektonClient tektonClient;
    private final PipelineRunWriter writer;
    private final String provider;

    public PipelineSyncService(GitHubActionsClient githubClient,
                               TektonClient tektonClient,
                               PipelineRunWriter writer,
                               @Value("${pipeline.provider:github}") String provider) {
        this.githubClient = githubClient;
        this.tektonClient = tektonClient;
        this.writer = writer;
        this.provider = provider.trim().toLowerCase();
    }

    public int sync() {
        if ("tekton".equals(provider) || "kubernetes".equals(provider)) {
            List<WorkflowRunDto> runs = tektonClient.fetchRuns();
            return writer.saveRuns(runs, "tekton", "kubernetes");
        }

        List<WorkflowRunDto> runs = githubClient.fetchRuns();
        return writer.saveRuns(runs, githubClient.owner(), githubClient.repository());
    }
}
