package com.pipeline.guardian_ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalysisResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long runId;
    @Column(columnDefinition = "text") private String rootCause;
    private Double confidence;
    @Column(columnDefinition = "text") private String evidence;
    @Column(columnDefinition = "text") private String recommendation;
    private String source;
    private LocalDateTime createdAt;
}

