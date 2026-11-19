package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.dto.ReliabilityDto;
import com.pipeline.guardian_ai.repository.PipelineRunRepository;
import org.springframework.stereotype.Service;

@Service
public class ReliabilityService {
    private final PipelineRunRepository repository;

    public ReliabilityService(PipelineRunRepository repository) {
        this.repository = repository;
    }

    public ReliabilityDto calculate() {
        long total = repository.count();
        long successful = repository.countByConclusionIgnoreCase("success");
        long failed = repository.countByConclusionIgnoreCase("failure");
        double score = total == 0 ? 0 : successful * 100.0 / total;
        return new ReliabilityDto(total, successful, failed,
                Math.round(score * 100.0) / 100.0);
    }
}

