package com.pipeline.guardian_ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pipeline.guardian_ai.dto.WorkflowRunDto;
import com.pipeline.guardian_ai.entity.PipelineRun;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineRunWriterTest {
    @Test
    void updatesExistingRunAndClearsDurationForAnInProgressRerun() {
        var repository = mock(PipelineRunRepository.class);
        var existing = new PipelineRun();
        existing.setRunId(42L);
        existing.setDurationSeconds(30L);
        existing.setConclusion("failure");
        when(repository.findByRunIdIn(any())).thenReturn(List.of(existing));
        var run = new WorkflowRunDto(42L, "Build", "in_progress", null, "main",
                Instant.parse("2025-01-01T00:00:00Z"), null, null);

        assertEquals(1, new PipelineRunWriter(repository).saveRuns(List.of(run), "owner", "repo"));
        assertEquals("in_progress", existing.getStatus());
        assertNull(existing.getConclusion());
        assertNull(existing.getDurationSeconds());
        assertEquals(LocalDateTime.parse("2025-01-01T00:00:00"), existing.getCreatedAt());
        assertEquals("owner", existing.getRepositoryOwner());
        verify(repository).saveAll(any());
    }

    @Test
    void estimatesCompletedRunDuration() {
        var repository = mock(PipelineRunRepository.class);
        when(repository.findByRunIdIn(any())).thenReturn(List.of());
        var start = Instant.parse("2025-01-01T00:00:00Z");
        var run = new WorkflowRunDto(1L, "Build", "completed", "success", "main",
                start, start, start.plusSeconds(45));
        new PipelineRunWriter(repository).saveRuns(List.of(run), "owner", "repo");
        verify(repository).saveAll(argThat(saved -> {
            var runSaved = saved.iterator().next();
            return Long.valueOf(45L).equals(runSaved.getDurationSeconds())
                    && "success".equals(runSaved.getConclusion());
        }));
    }
}
