package com.pipeline.guardian_ai.repository;

import com.pipeline.guardian_ai.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PipelineRunRepository
        extends JpaRepository<PipelineRun, Long> {

    List<PipelineRun> findByRunIdIn(Collection<Long> runIds);

    long countByConclusionIgnoreCase(String conclusion);

    java.util.Optional<PipelineRun> findTopByConclusionIgnoreCaseOrderByCreatedAtDesc(String conclusion);

    java.util.Optional<PipelineRun> findByRunId(Long runId);

    java.util.Optional<PipelineRun> findTopByConclusionIgnoreCaseAndCreatedAtBeforeOrderByCreatedAtDesc(
            String conclusion, java.time.LocalDateTime createdAt);

}