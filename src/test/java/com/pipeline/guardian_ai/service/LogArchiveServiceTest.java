package com.pipeline.guardian_ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class LogArchiveServiceTest {
    @Test
    void separatesLogsFromJUnitXmlReports() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            addEntry(zip, "step.txt", "BUILD FAILURE");
            addEntry(zip, "test-results/junit.xml",
                    "<testsuite name=\"unit\" failures=\"1\"><testcase name=\"broken\">"
                            + "<failure message=\"assertion failed\"/></testcase></testsuite>");
        }

        LogArchiveService archives = new LogArchiveService();
        assertThat(archives.extractText(bytes.toByteArray())).contains("BUILD FAILURE");
        assertThat(archives.extractTestReports(bytes.toByteArray()))
                .singleElement().asString().contains("<testsuite");
    }

    private void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
