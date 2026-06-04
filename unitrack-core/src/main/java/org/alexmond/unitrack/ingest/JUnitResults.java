package org.alexmond.unitrack.ingest;

import java.util.List;

/** Aggregated parse result for one or more JUnit XML files. */
public record JUnitResults(List<ParsedSuite> suites) {

    public int totalTests() {
        return suites.stream().mapToInt(ParsedSuite::tests).sum();
    }

    public int failures() {
        return suites.stream().mapToInt(ParsedSuite::failures).sum();
    }

    public int errors() {
        return suites.stream().mapToInt(ParsedSuite::errors).sum();
    }

    public int skipped() {
        return suites.stream().mapToInt(ParsedSuite::skipped).sum();
    }

    public long durationMs() {
        return suites.stream().mapToLong(ParsedSuite::durationMs).sum();
    }

    public int passed() {
        return totalTests() - failures() - errors() - skipped();
    }

    public boolean isEmpty() {
        return suites.isEmpty();
    }
}
