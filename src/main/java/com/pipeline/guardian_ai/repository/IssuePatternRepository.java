package com.pipeline.guardian_ai.repository;

import com.pipeline.guardian_ai.entity.IssuePattern;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuePatternRepository extends JpaRepository<IssuePattern, Long> {
    Optional<IssuePattern> findByErrorHash(String errorHash);
}

