package com.pipeline.guardian_ai.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class TestReportParserService {
    private static final Logger logger = LoggerFactory.getLogger(TestReportParserService.class);

    public List<String> analyze(String reportText) {
        if (reportText == null || reportText.isBlank()) return List.of();
        if (!reportText.contains("<testsuite") && !reportText.contains("<testsuites")
                && !reportText.contains("<testcase")) return List.of();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            var document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(reportText.getBytes(StandardCharsets.UTF_8)));
            List<String> findings = new ArrayList<>();

            NodeList suites = document.getElementsByTagName("testsuite");
            for (int i = 0; i < suites.getLength() && findings.size() < 20; i++) {
                Element suite = (Element) suites.item(i);
                int failures = parseCount(suite.getAttribute("failures"));
                int errors = parseCount(suite.getAttribute("errors"));
                if (failures + errors > 0) {
                    findings.add("Test suite " + clean(suite.getAttribute("name"))
                            + " reported " + failures + " failure(s) and " + errors + " error(s)");
                }
            }

            NodeList cases = document.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength() && findings.size() < 20; i++) {
                Element testCase = (Element) cases.item(i);
                Element failure = firstChild(testCase, "failure");
                Element error = firstChild(testCase, "error");
                Element problem = failure != null ? failure : error;
                if (problem == null) continue;

                String className = clean(testCase.getAttribute("classname"));
                String testName = clean(testCase.getAttribute("name"));
                String label = (className.isBlank() ? "" : className + ".") + testName;
                String message = clean(problem.getAttribute("message"));
                if (message.isBlank()) {
                    message = clean(problem.getTextContent()).replaceAll("\\s+", " ").trim();
                }
                if (message.length() > 300) message = message.substring(0, 300);
                findings.add("Failed test " + label + (message.isBlank() ? "" : ": " + message));
            }
            return findings;
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException ex) {
            logger.warn("Could not parse a pipeline test report: {}", ex.getMessage());
            return List.of();
        }
    }

    private int parseCount(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
