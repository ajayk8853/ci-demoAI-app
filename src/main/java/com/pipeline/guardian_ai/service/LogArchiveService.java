package com.pipeline.guardian_ai.service;

import java.io.ByteArrayInputStream;
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
        if (archive == null || archive.length == 0) return "";
        List<String> texts = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/").toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || name.contains("../") || name.startsWith("/")) continue;
                if (!name.endsWith(".txt") && !name.endsWith(".log")
                        && !name.endsWith(".xml") && !name.endsWith(".json")) continue;
                String text = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                texts.add(text.length() > 500_000 ? text.substring(0, 500_000) : text);
                if (texts.stream().mapToInt(String::length).sum() > 2_000_000) break;
            }
        } catch (Exception ignored) {
            return "";
        }
        return String.join("\n", texts);
    }
}
