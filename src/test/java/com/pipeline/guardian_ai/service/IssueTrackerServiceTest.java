package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.repository.IssuePatternRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueTrackerServiceTest {

    @Test
    void fingerprintIsStableForEquivalentErrors() {
        IssuePatternRepository repository = mock(IssuePatternRepository.class);
        IssueTrackerService service = new IssueTrackerService(repository);

        String a = service.fingerprint("Error 1234 at commit abc1234");
        String b = service.fingerprint("error 9999 at commit fffaaaa");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void incrementsOccurrenceForRecurringIssue() {
        IssuePatternRepository repository = mock(IssuePatternRepository.class);
        IssuePattern existing = new IssuePattern();
        existing.setErrorHash("hash");
        existing.setOccurrences(1);
        when(repository.findByErrorHash(any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IssueTrackerService service = new IssueTrackerService(repository);
        IssueTrackerService.RecurringIssue result = service.track("Permission denied", "Security or Permission");

        assertThat(result.recurring()).isTrue();
        assertThat(result.previousOccurrences()).isEqualTo(1);
        verify(repository).save(existing);
        assertThat(existing.getOccurrences()).isEqualTo(2);
    }
}

