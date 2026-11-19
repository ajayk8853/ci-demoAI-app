package com.pipeline.guardian_ai.controller;

import com.pipeline.guardian_ai.entity.PipelineRun;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import com.pipeline.guardian_ai.service.PipelineSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {

  private final PipelineRunRepository repository;
  private final PipelineSyncService syncService;

    @GetMapping
    public List<PipelineRun> getAllRuns() {

        return repository.findAll();
    }

    @PostMapping("/sync")
    public SyncResult syncRuns() {
        return new SyncResult(syncService.sync());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleSyncError(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    public record SyncResult(int synced) {}
}