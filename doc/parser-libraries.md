# Parser libraries vs hand-rolled — decision & format coverage

_Assessment date: 2026-06-27._

UniTrack parses CI artifacts in `unitrack-core` (`ingest/`). Today most parsers are
hand-rolled (XXE-hardened DOM for XML, a line scanner for LCOV, a custom CSV splitter for
JTL); JSON already uses Jackson.

## Posture: prefer a well-used third-party library over hand-rolled code

The default is **adopt the library**. The *only* thing that turns adoption into a real
question is **needing to extend/fork the library** to do what we need — consuming its output
through a thin adapter does **not** count as extending. (XXE protection must stay on for any
XML we still parse ourselves — see `ingest/XmlSupport`.)

| Format | Library | Adopt? |
|---|---|---|
| JaCoCo / Cobertura / LCOV / OpenCover XML | **`edu.hm.hafner:coverage-model`** — reads all four (Jenkins Coverage engine, MIT, maintained) | **ADOPT** — replaces 4 hand-rolled parsers; output→domain adapter, no extension. |
| JMeter JTL (CSV) | **`org.apache.commons:commons-csv`** | **ADOPT** — replaces the hand-rolled `splitCsv`; robust RFC-4180 quoting. |
| k6 JSON, JMH `-rf json` | **Jackson 3** (already used) | **KEEP** — already library-based. |
| JUnit / Surefire XML | `org.apache.maven.surefire:surefire-report-parser` | **REAL QUESTION (the exception)** — see below. |

### ADOPT: `edu.hm.hafner:coverage-model`

One dependency reads **JaCoCo + Cobertura + LCOV + OpenCover** (and Clover, and more) into a
`Node` tree; we map that tree to `CoverageReport`/`CoverageFileEntry` via an adapter. This
deletes four hand-rolled parsers and the coverage half of `XmlSupport`, and **unlocks
formats we don't have yet (e.g. Clover) for free**. It's the Jenkins Coverage plugin engine,
so it's hardened against real-world edge cases.

- No extension needed — we consume its output, we don't subclass it. ✅ (fits the posture)
- **One thing to validate during the swap:** coverage-model's aggregation/rounding may differ
  from our parsers by a fraction of a percent, which feeds the **quality gate** — so the
  migration must diff coverage numbers on real reports and confirm no gate PASS/FAIL flips
  (or accept + document the shift). This is a migration-validation step, not a blocker.

### ADOPT: `commons-csv` for JTL

Replace the custom quote-aware splitter with `CSVFormat`-driven parsing. commons-csv (~61 KB)
is lighter and more correct on quoting/escaping than rolling our own; opencsv's extra weight
is bean-binding we don't use.

### REAL QUESTION: JUnit / Surefire XML

This is the one place the exception bites. The only credible reader,
`surefire-report-parser`, is coupled to the **Surefire dialect** and takes a **directory**
(globs `TEST-*.xml`), while we ingest **streams from many producers** (Maven, Gradle, pytest
`--junitxml`, jest-junit, trx-converted). Making it cover our producers would mean
**extending/working around it** — exactly the case your rule flags. Options:
1. Keep the lenient hand-rolled DOM parser (handles every producer; what we do today).
2. Adopt `surefire-report-parser` for the Surefire path + keep a fallback for the rest.

Lean **(1)** until a producer-specific bug makes the hand-rolled leniency a maintenance cost;
revisit **(2)** if a clean multi-dialect JUnit-XML library appears.

### Not adoptable (not an extension question — the lib can't read)

- **`jmh-core`** is write-only (`ResultFormat.writeOut` has no read counterpart) and heavy
  (pulls commons-math3). It cannot deserialize its own JSON → bind the flat array with
  Jackson POJOs.

## Formats not yet ingested (gaps)

**UniTrack is polyglot by intent — Go, .NET, Node, Python, JVM, Ruby, PHP, anything.** It is
not a JVM-targeted tool; broad format support is a **first-class goal** ("the more the
merrier"). So the bias is to **add format parsers liberally** — preferring a maintained
library per format (per the posture above), else letting the format dictate the approach
(JSON → Jackson POJOs; simple XML → DOM).

Where we stand per ecosystem (✓ native · ⇢ works only via a converter to a format we accept ·
✗ missing):

| Ecosystem | Tests | Coverage | Bench / perf |
|---|---|---|---|
| JVM | JUnit XML ✓ | JaCoCo ✓ | JMH ✓ |
| .NET | ✗ TRX / xUnit / NUnit (⇢ convert→JUnit) | OpenCover ✓ · Cobertura ✓ · LCOV ✓ (Coverlet) | ✗ BenchmarkDotNet |
| Go | ✗ `test -json` (⇢ gotestsum→JUnit) | ✗ native `cover.out` (⇢ gocover-cobertura) | ✗ `test -bench` |
| Node / JS / TS | ⇢ jest/mocha→JUnit | LCOV ✓ · Cobertura ✓ (nyc/c8) | ✗ (tinybench/benchmark.js JSON) |
| Python | pytest `--junitxml` ✓ | coverage.py Cobertura/LCOV ✓ | ✗ pytest-benchmark JSON |
| PHP | PHPUnit JUnit ✓ | ✗ Clover (Cobertura ✓) | — |
| Ruby | ⇢ RSpec→JUnit | SimpleCov→Cobertura/LCOV ✓ | — |
| Any | ✗ TAP · ✗ CTRF · ✗ Allure | — | — |

Reading the matrix: **coverage is broadly covered** (often natively; coverage-model widens it
further — Clover etc. for free). The real gaps are **native test-result formats** (everything
non-JUnit is convert-only or missing) and **per-language micro-bench** (only JVM/JMH today).

Highest-leverage additions, treating every ecosystem as first-class:
- **.NET TRX** — native .NET tests (pairs with our already-strong .NET coverage).
- **Go `test -json`** + native `cover.out` + **`test -bench`** — Go is entirely converter-dependent today.
- **CTRF** (Common Test Report Format) — language-agnostic unified JSON test standard; a trivial Jackson bind and on-brand for a polyglot tool.
- **Clover XML** — free once coverage-model lands.
- JMH analogs: **BenchmarkDotNet**, **pytest-benchmark**, **Go bench**, **Google Benchmark** (all JSON → Jackson).
- More load sources: **Gatling** (deferred — brittle simulation.log), **Locust** CSV, **Artillery** JSON.
