package com.pipeline.guardian_ai.controller;

import com.pipeline.guardian_ai.dto.AnalysisDto;
import com.pipeline.guardian_ai.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class AnalysisController {
    private final AiAnalysisService analysisService;

    @PostMapping("/analyze/{runId}")
    public AnalysisDto analyze(@PathVariable Long runId) {
        return analysisService.analyze(runId);
    }
}

