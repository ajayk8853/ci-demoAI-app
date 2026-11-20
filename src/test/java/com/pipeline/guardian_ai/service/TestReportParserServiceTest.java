package com.pipeline.guardian_ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestReportParserServiceTest {
    private final TestReportParserService parser = new TestReportParserService();

    @Test
    void extractsSuiteAndCaseFailures() {
        String report = """
                <testsuites>
                  <testsuite name="unit" failures="1" errors="0">
                    <testcase classname="BuildTest" name="rejectsInvalidInput">
                      <failure message="expected validation error">assertion failed</failure>
                    </testcase>
                  </testsuite>
                </testsuites>
                """;

        assertThat(parser.analyze(report))
                .contains("Test suite unit reported 1 failure(s) and 0 error(s)")
                .anySatisfy(finding -> {
                    assertThat(finding).contains("BuildTest.rejectsInvalidInput");
                    assertThat(finding).contains("expected validation error");
                });
    }

    @Test
    void ignoresNonXmlAndBlocksExternalEntities() {
        assertThat(parser.analyze("build succeeded")).isEmpty();
        assertThat(parser.analyze("""
                <!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <testsuite name="hostile"><testcase name="test">&xxe;</testcase></testsuite>
                """)).isEmpty();
    }
}
