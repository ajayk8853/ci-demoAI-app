package com.pipeline.guardian_ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "issue_occurrences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"error_hash", "run_id"}))
@Getter
@Setter
@NoArgsConstructor
public class IssueOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_hash", nullable = false, length = 64)
    private String errorHash;

    @Column(name = "run_id", nullable = false)
    private Long runId;
}
