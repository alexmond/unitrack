package org.alexmond.unitrack.ingest;

import java.util.List;

/** A parsed &lt;testsuite&gt; with its cases. */
public record ParsedSuite(
        String name,
        int tests,
        int failures,
        int errors,
        int skipped,
        long durationMs,
        List<ParsedCase> cases) {
}
