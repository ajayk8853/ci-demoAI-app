package com.pipeline.guardian_ai.repository;

import com.pipeline.guardian_ai.entity.AnalysisResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    Optional<AnalysisResult> findTopByRunIdOrderByCreatedAtDesc(Long runId);
}

