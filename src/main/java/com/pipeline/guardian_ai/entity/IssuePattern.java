package com.pipeline.guardian_ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "issue_patterns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IssuePattern {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true) private String errorHash;
    @Column(columnDefinition = "text") private String errorMessage;
    private String rootCause;
    private Integer occurrences;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
}

