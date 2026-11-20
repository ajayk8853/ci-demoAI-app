package com.pipeline.guardian_ai.repository;

import com.pipeline.guardian_ai.entity.IssueOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueOccurrenceRepository extends JpaRepository<IssueOccurrence, Long> {
    boolean existsByErrorHashAndRunId(String errorHash, Long runId);
}
