package com.pipeline.guardian_ai.controller;

import com.pipeline.guardian_ai.dto.DashboardDto;
import com.pipeline.guardian_ai.dto.ReliabilityDto;
import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.repository.IssuePatternRepository;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import com.pipeline.guardian_ai.service.ReliabilityService;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {
    private final PipelineRunRepository pipelineRuns;
    private final IssuePatternRepository issues;
    private final ReliabilityService reliability;

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        ReliabilityDto score = reliability.calculate();
        var runs = pipelineRuns.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        var recent = runs.stream().limit(50).toList();
        var issueHistory = issues.findAll(Sort.by(Sort.Direction.DESC, "lastSeen"));
        long recurring = issueHistory.stream().filter(i -> i.getOccurrences() != null && i.getOccurrences() > 1).count();
        return new DashboardDto(score.totalRuns(), score.successfulRuns(), score.failedRuns(),
                score.reliabilityScore(), recurring, recent, issueHistory);
    }

    @GetMapping("/issues")
    public java.util.List<IssuePattern> issues() {
        return issues.findAll(Sort.by(Sort.Direction.DESC, "lastSeen"));
    }
}

