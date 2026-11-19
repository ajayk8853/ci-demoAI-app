package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import com.pipeline.guardian_ai.entity.PipelineRun;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineRunWriter {
    private final PipelineRunRepository repository;

    public PipelineRunWriter(PipelineRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int saveRuns(List<WorkflowRunDto> runs, String owner, String repositoryName) {
        Map<Long, PipelineRun> existing = new HashMap<>();
        // PostgreSQL has a limit on the number of bind parameters in a query.
        for (int start = 0; start < runs.size(); start += 500) {
            List<Long> ids = runs.subList(start, Math.min(start + 500, runs.size()))
                    .stream().map(WorkflowRunDto::id).toList();
            repository.findByRunIdIn(ids).forEach(run -> existing.put(run.getRunId(), run));
        }
        for (WorkflowRunDto source : runs) {
            PipelineRun target = existing.computeIfAbsent(source.id(), id -> {
                PipelineRun run = new PipelineRun();
                run.setRunId(id);
                return run;
            });
            target.setWorkflowName(source.name());
            target.setStatus(source.status());
            target.setConclusion(source.conclusion());
            target.setBranchName(source.headBranch());
            target.setRepositoryOwner(owner);
            target.setRepositoryName(repositoryName);
            target.setCreatedAt(LocalDateTime.ofInstant(source.createdAt(), ZoneOffset.UTC));
            Long duration = null;
            if ("completed".equals(source.status()) && source.runStartedAt() != null
                    && source.updatedAt() != null && !source.updatedAt().isBefore(source.runStartedAt())) {
                duration = Duration.between(source.runStartedAt(), source.updatedAt()).getSeconds();
            }
            target.setDurationSeconds(duration);
        }
        repository.saveAll(existing.values());
        return existing.size();
    }
}
