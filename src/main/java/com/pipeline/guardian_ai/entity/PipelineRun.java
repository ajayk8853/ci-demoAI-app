package com.pipeline.guardian_ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_runs", uniqueConstraints = @UniqueConstraint(columnNames = "run_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;

    private String workflowName;

    private String status;

    private String conclusion;

    private String repositoryOwner;

    private String repositoryName;

    private String branchName;

    private Long durationSeconds;

    private LocalDateTime createdAt;
}
