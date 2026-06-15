package org.alexmond.unitrack.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.PerfRun;
import org.alexmond.unitrack.domain.PerfTransaction;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.repository.PerfRunRepository;
import org.alexmond.unitrack.repository.PerfTransactionRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parses and stores an uploaded performance-test result as a {@link PerfRun} + per-label
 * rows.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PerfIngestService {

	private final ProjectRepository projects;

	private final PerfRunRepository perfRuns;

	private final PerfTransactionRepository perfTransactions;

	private final PerfParsers perfParsers;

	/**
	 * Visibility assigned to auto-created projects on first ingest. Defaults to PRIVATE.
	 */
	@Value("${unitrack.security.default-visibility:PRIVATE}")
	private Visibility defaultVisibility = Visibility.PRIVATE;

	@Transactional
	public PerfRun ingest(IngestRequest meta, List<Supplier<InputStream>> perfStreams) {
		if (meta.project() == null || meta.project().isBlank()) {
			throw new IngestException("'project' is required");
		}
		if (perfStreams.isEmpty()) {
			throw new IngestException("At least one performance result file is required");
		}

		Project project = findOrCreateProject(meta.project(), meta.repoUrl());
		PerfParsers.Parsed parsed = parse(perfStreams.get(0));
		PerfResults r = parsed.results();

		PerfRun run = new PerfRun(project, blankToNull(meta.branch()), meta.flag(), blankToNull(meta.commit()),
				blankToNull(meta.buildUrl()), blankToNull(meta.ciProvider()), parsed.format());
		run.setRunKey(blankToNull(meta.runKey()));
		run.setSampleCount(r.sampleCount());
		run.setErrorCount(r.errorCount());
		run.setErrorPct(r.errorPct());
		run.setThroughputRps(r.throughputRps());
		run.setDurationMs(r.durationMs());
		run.setMeanMs(r.meanMs());
		run.setP50Ms(r.p50Ms());
		run.setP90Ms(r.p90Ms());
		run.setP95Ms(r.p95Ms());
		run.setP99Ms(r.p99Ms());
		run.setMinMs(r.minMs());
		run.setMaxMs(r.maxMs());
		perfRuns.save(run);

		List<PerfTransaction> rows = new ArrayList<>();
		for (PerfResults.LabelStats s : r.labels()) {
			PerfTransaction t = new PerfTransaction(run, s.label());
			t.setSampleCount(s.sampleCount());
			t.setErrorCount(s.errorCount());
			t.setErrorPct(s.errorPct());
			t.setMeanMs(s.meanMs());
			t.setP50Ms(s.p50Ms());
			t.setP90Ms(s.p90Ms());
			t.setP95Ms(s.p95Ms());
			t.setP99Ms(s.p99Ms());
			rows.add(t);
		}
		perfTransactions.saveAll(rows);

		log.info("Ingested perf run {} for project '{}' ({} samples, {} errors, {} labels, format {})", run.getId(),
				project.getName(), run.getSampleCount(), run.getErrorCount(), rows.size(), parsed.format());
		return run;
	}

	private PerfParsers.Parsed parse(Supplier<InputStream> supplier) {
		try (InputStream in = supplier.get()) {
			return perfParsers.parse(in);
		}
		catch (IOException ex) {
			throw new IngestException("Failed reading performance upload: " + ex.getMessage(), ex);
		}
	}

	private Project findOrCreateProject(String name, String repoUrl) {
		return projects.findByName(name).orElseGet(() -> {
			Project created = new Project(name, blankToNull(repoUrl));
			created.setVisibility(defaultVisibility);
			return projects.save(created);
		});
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
