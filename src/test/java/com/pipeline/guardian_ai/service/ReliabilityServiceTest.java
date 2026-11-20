package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.ReliabilityDto;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReliabilityServiceTest {

    @Test
    void calculatesReliabilityFromRepositoryCounts() {
        PipelineRunRepository repository = mock(PipelineRunRepository.class);
        when(repository.count()).thenReturn(14L);
        when(repository.countByConclusionIgnoreCase("success")).thenReturn(8L);
        when(repository.countByConclusionIgnoreCase("failure")).thenReturn(2L);

        ReliabilityService service = new ReliabilityService(repository);
        ReliabilityDto dto = service.calculate();

        assertThat(dto.totalRuns()).isEqualTo(14);
        assertThat(dto.successfulRuns()).isEqualTo(8);
        assertThat(dto.failedRuns()).isEqualTo(2);
        assertThat(dto.reliabilityScore()).isEqualTo(80.0);
    }
}
