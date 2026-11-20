package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.entity.IssueOccurrence;
import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.repository.IssuePatternRepository;
import com.pipeline.guardian_ai.repository.IssueOccurrenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueTrackerService {
    private final IssuePatternRepository repository;
    private final IssueOccurrenceRepository occurrences;

    public IssueTrackerService(IssuePatternRepository repository, IssueOccurrenceRepository occurrences) {
        this.repository = repository;
        this.occurrences = occurrences;
    }

    @Transactional
    public RecurringIssue track(Long runId, String errorMessage, String rootCause) {
        String normalized = normalize(errorMessage);
        String hash = sha256(normalized);
        IssuePattern issue = repository.findByErrorHash(hash).orElseGet(() -> {
            IssuePattern created = new IssuePattern();
            created.setErrorHash(hash);
            created.setErrorMessage(normalized);
            created.setRootCause(rootCause);
            created.setOccurrences(0);
            created.setFirstSeen(LocalDateTime.now(ZoneOffset.UTC));
            return created;
        });
        int count = issue.getOccurrences() == null ? 0 : issue.getOccurrences();
        if (!occurrences.existsByErrorHashAndRunId(hash, runId)) {
            IssueOccurrence occurrence = new IssueOccurrence();
            occurrence.setErrorHash(hash);
            occurrence.setRunId(runId);
            occurrences.save(occurrence);
            count++;
            issue.setOccurrences(count);
        }
        issue.setRootCause(rootCause);
        issue.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        repository.save(issue);
        return new RecurringIssue(count > 1, Math.max(0, count - 1));
    }

    public String fingerprint(String errorMessage) {
        return sha256(normalize(errorMessage));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown pipeline failure";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\b[a-f0-9]{7,40}\\b", "<sha>")
                .replaceAll("\\d+", "<number>")
                .replaceAll("\\s+", " ").trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record RecurringIssue(boolean recurring, int previousOccurrences) {}
}
