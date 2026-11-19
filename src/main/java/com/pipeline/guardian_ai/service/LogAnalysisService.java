package com.pipeline.guardian_ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LogAnalysisService {
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public LogAnalysisService() {
        patterns.put("Memory Exhaustion", Pattern.compile("(?i)OutOfMemoryError|heap space|metaspace|OOMKilled"));
        patterns.put("Network or Service Connectivity", Pattern.compile("(?i)connection refused|could not resolve host|timed out|timeout connecting"));
        patterns.put("Security or Permission", Pattern.compile("(?i)permission denied|unauthorized|forbidden|authentication failed"));
        patterns.put("Dependency or Package Installation", Pattern.compile("(?i)npm ERR|Could not resolve dependency|dependency resolution|package .*not found"));
        patterns.put("Docker Configuration", Pattern.compile("(?i)docker build failed|manifest unknown|docker daemon|container.*not found"));
        patterns.put("Test Failure", Pattern.compile("(?i)tests? run:.*failures:|AssertionError|FAILED .*test|BUILD FAILURE"));
        patterns.put("Compilation Failure", Pattern.compile("(?i)compilation failed|cannot find symbol|syntax error|TypeScript error"));
    }

    public List<String> analyze(String log) {
        String text = log == null ? "" : log;
        List<String> matches = new ArrayList<>();
        patterns.forEach((name, pattern) -> {
            if (pattern.matcher(text).find()) matches.add(name);
        });
        return matches;
    }
}

