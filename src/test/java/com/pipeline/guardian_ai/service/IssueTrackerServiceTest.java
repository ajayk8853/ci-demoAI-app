package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.repository.IssueOccurrenceRepository;
import com.pipeline.guardian_ai.repository.IssuePatternRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IssueTrackerServiceTest {

    @Test
    void fingerprintIsStableForEquivalentErrors() {
        IssuePatternRepository repository = mock(IssuePatternRepository.class);
        IssueTrackerService service = new IssueTrackerService(repository, mock(IssueOccurrenceRepository.class));

        String a = service.fingerprint("Error 1234 at commit abc1234");
        String b = service.fingerprint("error 9999 at commit fffaaaa");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void incrementsOccurrenceForRecurringIssue() {
        IssuePatternRepository repository = mock(IssuePatternRepository.class);
        IssueOccurrenceRepository occurrences = mock(IssueOccurrenceRepository.class);
        IssuePattern existing = new IssuePattern();
        existing.setErrorHash("hash");
        existing.setOccurrences(1);
        when(repository.findByErrorHash(any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(occurrences.existsByErrorHashAndRunId(any(), eq(200L))).thenReturn(false);

        IssueTrackerService service = new IssueTrackerService(repository, occurrences);
        IssueTrackerService.RecurringIssue result = service.track(200L, "Permission denied", "Security or Permission");

        assertThat(result.recurring()).isTrue();
        assertThat(result.previousOccurrences()).isEqualTo(1);
        verify(repository).save(existing);
        assertThat(existing.getOccurrences()).isEqualTo(2);
    }

    @Test
    void doesNotCountAnalyzingTheSameRunTwiceAsARecurrence() {
        IssuePatternRepository repository = mock(IssuePatternRepository.class);
        IssueOccurrenceRepository occurrences = mock(IssueOccurrenceRepository.class);
        IssuePattern existing = new IssuePattern();
        existing.setOccurrences(1);
        when(repository.findByErrorHash(any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(occurrences.existsByErrorHashAndRunId(any(), eq(100L))).thenReturn(true);

        IssueTrackerService.RecurringIssue result =
                new IssueTrackerService(repository, occurrences).track(100L, "Permission denied", "Security or Permission");

        assertThat(result.recurring()).isFalse();
        assertThat(result.previousOccurrences()).isZero();
        assertThat(existing.getOccurrences()).isEqualTo(1);
        verify(occurrences, never()).save(any());
    }
}
