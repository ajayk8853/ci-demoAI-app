package com.pipeline.guardian_ai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

@Service
public class LogArchiveService {
    public String extractText(byte[] archive) {
        return String.join("\n", extractEntries(archive, false));
    }

    public List<String> extractTestReports(byte[] archive) {
        return extractEntries(archive, true);
    }

    private List<String> extractEntries(byte[] archive, boolean reportsOnly) {
        if (archive == null || archive.length == 0) return List.of();
        List<String> texts = new ArrayList<>();
        int totalCharacters = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/").toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || name.contains("../") || name.startsWith("/")) continue;
                boolean xml = name.endsWith(".xml");
                if (reportsOnly ? !xml : !(name.endsWith(".txt") || name.endsWith(".log") || xml || name.endsWith(".json"))) {
                    continue;
                }
                byte[] content = zip.readNBytes(500_001);
                String text = new String(content, StandardCharsets.UTF_8);
                if (content.length > 500_000) {
                    text = text.substring(0, 500_000);
                }
                texts.add(text);
                totalCharacters += text.length();
                if (totalCharacters >= 2_000_000) break;
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read downloaded pipeline log archive", ex);
        }
        return texts;
    }
}
