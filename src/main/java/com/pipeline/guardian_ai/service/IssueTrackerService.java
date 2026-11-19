package com.pipeline.guardian_ai.service;

import com.pipeline.guardian_ai.entity.IssuePattern;
import com.pipeline.guardian_ai.repository.IssuePatternRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueTrackerService {
    private final IssuePatternRepository repository;

    public IssueTrackerService(IssuePatternRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RecurringIssue track(String errorMessage, String rootCause) {
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
        int previous = issue.getOccurrences() == null ? 0 : issue.getOccurrences();
        issue.setOccurrences(previous + 1);
        issue.setRootCause(rootCause);
        issue.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        repository.save(issue);
        return new RecurringIssue(previous + 1 > 1, previous);
    }

    public String fingerprint(String errorMessage) {
        return sha256(normalize(errorMessage));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "unknown pipeline failure";
        return value.toLowerCase()
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

