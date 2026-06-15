package org.alexmond.unitrack.web.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.ProjectRole;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.Visibility;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.ingest.PerfIngestService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.web.account.MembershipService;
import org.alexmond.unitrack.web.account.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seeds a test user and a few varied sample projects when
 * {@code unitrack.demo.enabled=true}.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

	private final DemoProperties props;

	private final UserService users;

	private final ProjectRepository projects;

	private final IngestService ingest;

	private final PerfIngestService perfIngest;

	private final MembershipService membership;

	@Override
	public void run(ApplicationArguments args) {
		if (!props.isEnabled()) {
			return;
		}
		if (users.findByUsername(props.getTestUsername()).isEmpty()) {
			users.create(props.getTestUsername(), "Test User", "test@example.com", props.getTestPassword(), Role.USER);
			log.info("Demo: created user '{}'", props.getTestUsername());
		}
		seedCheckout();
		seedBilling();
		seedFrontend();
	}

	private void seedCheckout() {
		String name = "checkout-service";
		if (projects.findByName(name).isPresent()) {
			return;
		}
		String repo = "https://github.com/acme/checkout-service";
		List<Case> base = List.of(new Case("com.checkout.CartTest", "addItem", false, null, null),
				new Case("com.checkout.CartTest", "removeItem", false, null, null),
				new Case("com.checkout.PaymentTest", "charge", false, null, null));
		ingest(name, repo, "main", "default", "a1c0de1", base, 720, 280, 60, 20);

		List<Case> withFail = withExtra(base, new Case("com.checkout.PaymentTest", "refund", true,
				"org.opentest4j.AssertionFailedError", "expected: <true> but was: <false>"));
		ingest(name, repo, "main", "default", "a2c0de2", withFail, 750, 250, 64, 16);

		// Same commit, flaky: one run fails, the next passes.
		Case flakyFail = new Case("com.checkout.FlakyTest", "sometimes", true, "java.lang.AssertionError",
				"intermittent: timing");
		Case flakyPass = new Case("com.checkout.FlakyTest", "sometimes", false, null, null);
		ingest(name, repo, "main", "default", "a3c0de3", withExtra(base, flakyFail), 770, 230, 66, 14);
		ingest(name, repo, "main", "default", "a3c0de3", withExtra(base, flakyPass), 775, 225, 67, 13);

		ingest(name, repo, "main", "default", "a4c0de4", base, 800, 200, 70, 10);

		// Performance runs (JMeter JTL) for the same service — a clean latency trend that
		// regresses sharply at a4 (p95 spike + errors) and recovers at a5, so the perf
		// trend chart and the regression flag both have something to show.
		ingestPerf(name, repo, "a1c0de1", 1.00, 0.000);
		ingestPerf(name, repo, "a2c0de2", 1.06, 0.000);
		ingestPerf(name, repo, "a3c0de3", 1.03, 0.005);
		ingestPerf(name, repo, "a4c0de4", 1.45, 0.030);
		ingestPerf(name, repo, "a5c0de5", 1.10, 0.000);
		// PUBLIC: visible to everyone, even signed-out — the open sample project.
		configure(name, Visibility.PUBLIC, null);
	}

	private void seedBilling() {
		String name = "billing-api";
		if (projects.findByName(name).isPresent()) {
			return;
		}
		String repo = "https://github.com/acme/billing-api";
		List<Case> ok = List.of(new Case("com.billing.LedgerTest", "post", false, null, null),
				new Case("com.billing.LedgerTest", "balance", false, null, null));
		ingest(name, repo, "main", "default", "b1c0de1",
				withExtra(ok,
						new Case("com.billing.InvoiceTest", "generate", true, "java.lang.NullPointerException",
								"Cannot invoke getTotal() on null at id 101"),
						new Case("com.billing.TaxTest", "calc", true, "java.lang.OutOfMemoryError", "Java heap space")),
				600, 400, 50, 30);
		ingest(name, repo, "main", "default", "b2c0de2", withExtra(ok, new Case("com.billing.InvoiceTest", "generate",
				true, "java.lang.NullPointerException", "Cannot invoke getTotal() on null at id 254")), 610, 390, 52,
				28);
		// Recovery: the NPE is fixed and coverage climbs over the next two commits.
		ingest(name, repo, "main", "default", "b3c0de3",
				withExtra(ok, new Case("com.billing.InvoiceTest", "generate", false, null, null)), 660, 340, 58, 22);
		ingest(name, repo, "main", "default", "b4c0de4",
				withExtra(ok, new Case("com.billing.InvoiceTest", "generate", false, null, null),
						new Case("com.billing.RefundTest", "process", false, null, null)),
				710, 290, 63, 17);
		// PRIVATE, owned by the test user: visible to them (and admins), hidden from
		// anonymous.
		configure(name, Visibility.PRIVATE, ProjectRole.OWNER);
	}

	private void seedFrontend() {
		String name = "web-frontend";
		if (projects.findByName(name).isPresent()) {
			return;
		}
		List<Case> cases = List.of(new Case("com.web.HomeTest", "render", false, null, null),
				new Case("com.web.NavTest", "links", false, null, null),
				new Case("com.web.CartTest", "badge", false, null, null));
		String repo = "https://github.com/acme/web-frontend";
		ingest(name, repo, "main", "frontend", "f1c0de1", cases, 850, 150, 75, 5);
		// A flaky render test, then a clean run on the next two commits with rising
		// coverage.
		ingest(name, repo, "main", "frontend", "f2c0de2", withExtra(cases,
				new Case("com.web.SearchTest", "suggest", true, "java.lang.AssertionError", "intermittent: debounce")),
				870, 130, 78, 4);
		ingest(name, repo, "main", "frontend", "f3c0de3",
				withExtra(cases, new Case("com.web.SearchTest", "suggest", false, null, null)), 900, 100, 81, 3);
		// PRIVATE, test user has READ only: they can view but not write (shows the role
		// split).
		configure(name, Visibility.PRIVATE, ProjectRole.READ);
	}

	/**
	 * Sets a seeded project's visibility and (optionally) grants the test user a role on
	 * it.
	 */
	private void configure(String name, Visibility visibility, ProjectRole testUserRole) {
		projects.findByName(name).ifPresent((project) -> {
			project.setVisibility(visibility);
			projects.save(project);
			if (testUserRole != null) {
				membership.addOrUpdate(project.getId(), props.getTestUsername(), testUserRole);
			}
		});
	}

	private List<Case> withExtra(List<Case> base, Case... extra) {
		List<Case> all = new ArrayList<>(base);
		all.addAll(List.of(extra));
		return all;
	}

	private void ingest(String project, String repo, String branch, String flag, String commit, List<Case> cases,
			int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
		IngestRequest meta = new IngestRequest(project, repo, branch, flag, commit, null, "demo", null);
		List<FileCov> files = distribute(lineCovered, lineMissed, branchCovered, branchMissed);
		// Each project uploads coverage in a different format, so the demo exercises
		// every
		// parser (the coverage field auto-detects format on ingest).
		String coverageReport = switch (coverageFormatFor(project)) {
			case COBERTURA -> cobertura(files);
			case LCOV -> lcov(files);
			default -> jacoco(files, lineCovered, lineMissed, branchCovered, branchMissed);
		};
		ingest.ingest(meta, List.of(supplier(junit(cases))), List.of(supplier(coverageReport)));
	}

	private static CovFmt coverageFormatFor(String project) {
		return switch (project) {
			case "billing-api" -> CovFmt.COBERTURA;
			case "web-frontend" -> CovFmt.LCOV;
			default -> CovFmt.JACOCO;
		};
	}

	/**
	 * Spreads the run totals across a fixed set of files (one well-covered, one poorly)
	 * so every coverage format renders a realistic per-file/per-package breakdown.
	 */
	private static List<FileCov> distribute(int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
		String[][] weights = { { "com/acme/service", "OrderService.java", "0.30", "0.08" },
				{ "com/acme/service", "PricingEngine.java", "0.12", "0.46" },
				{ "com/acme/web", "ApiController.java", "0.33", "0.21" },
				{ "com/acme/util", "JsonMapper.java", "0.25", "0.25" } };
		List<FileCov> files = new ArrayList<>();
		for (String[] w : weights) {
			double wc = Double.parseDouble(w[2]);
			double wm = Double.parseDouble(w[3]);
			files.add(new FileCov(w[0], w[1], (int) Math.round(lineCovered * wc), (int) Math.round(lineMissed * wm),
					(int) Math.round(branchCovered * wc), (int) Math.round(branchMissed * wm)));
		}
		return files;
	}

	private void ingestPerf(String project, String repo, String commit, double scale, double errFraction) {
		IngestRequest meta = new IngestRequest(project, repo, "main", "default", commit, null, "demo", null);
		perfIngest.ingest(meta, List.of(supplier(jtl(scale, errFraction))));
	}

	/**
	 * Builds a small JMeter JTL (CSV flavour): three labelled samplers, right-skewed
	 * latency so the percentiles look real. {@code scale} shifts every median (drives the
	 * trend) and {@code errFraction} marks a proportional slice of samples failed (drives
	 * the error rate).
	 */
	private static String jtl(double scale, double errFraction) {
		double[] mult = { 0.6, 0.7, 0.8, 0.85, 0.9, 0.95, 1.0, 1.0, 1.05, 1.1, 1.15, 1.2, 1.3, 1.4, 1.5, 1.7, 1.9, 2.2,
				2.6, 3.0 };
		String[] labels = { "GET /api/cart", "POST /api/checkout", "GET /api/products" };
		int[] medians = { 90, 240, 140 };
		int total = labels.length * mult.length;
		int errors = (int) Math.round(total * errFraction);
		int errStride = (errors > 0) ? Math.max(1, total / errors) : Integer.MAX_VALUE;
		StringBuilder sb = new StringBuilder("timeStamp,elapsed,label,responseCode,success\n");
		long ts = 1_700_000_000_000L;
		int i = 0;
		for (int l = 0; l < labels.length; l++) {
			for (double m : mult) {
				long elapsed = Math.round(medians[l] * scale * m);
				boolean fail = (errStride != Integer.MAX_VALUE) && (i % errStride == errStride - 1);
				sb.append(ts)
					.append(',')
					.append(elapsed)
					.append(',')
					.append(labels[l])
					.append(',')
					.append(fail ? "500" : "200")
					.append(',')
					.append(fail ? "false" : "true")
					.append('\n');
				ts += elapsed + 5;
				i++;
			}
		}
		return sb.toString();
	}

	private static Supplier<InputStream> supplier(String xml) {
		return () -> new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}

	private static String junit(List<Case> cases) {
		int failures = (int) cases.stream().filter(Case::fail).count();
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><testsuite name=\"demo\" tests=\"");
		sb.append(cases.size())
			.append("\" failures=\"")
			.append(failures)
			.append("\" errors=\"0\" skipped=\"0\" time=\"0.5\">");
		for (Case c : cases) {
			sb.append("<testcase classname=\"")
				.append(c.className())
				.append("\" name=\"")
				.append(c.name())
				.append("\" time=\"0.05\">");
			if (c.fail()) {
				sb.append("<failure type=\"")
					.append(c.type())
					.append("\" message=\"")
					.append(escape(c.message()))
					.append("\">")
					.append(escape(c.type() + ": " + c.message()))
					.append("</failure>");
			}
			sb.append("</testcase>");
		}
		return sb.append("</testsuite>").toString();
	}

	/**
	 * Builds a JaCoCo report whose report-level counters are exactly the requested totals
	 * (so the headline % is stable), with the per-{@code sourcefile} breakdown from
	 * {@code files}.
	 */
	private static String jacoco(List<FileCov> files, int lineCovered, int lineMissed, int branchCovered,
			int branchMissed) {
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><report name=\"demo\">");
		String lastPkg = null;
		for (FileCov f : files) {
			if (!f.pkg().equals(lastPkg)) {
				if (lastPkg != null) {
					sb.append("</package>");
				}
				sb.append("<package name=\"").append(f.pkg()).append("\">");
				lastPkg = f.pkg();
			}
			sb.append("<sourcefile name=\"")
				.append(f.file())
				.append("\">")
				.append(counter("LINE", f.lineCovered(), f.lineMissed()))
				.append(counter("BRANCH", f.branchCovered(), f.branchMissed()))
				.append("</sourcefile>");
		}
		if (lastPkg != null) {
			sb.append("</package>");
		}
		return sb.append(counter("INSTRUCTION", lineCovered * 3, lineMissed * 3))
			.append(counter("LINE", lineCovered, lineMissed))
			.append(counter("BRANCH", branchCovered, branchMissed))
			.append(counter("METHOD", lineCovered / 20, lineMissed / 20))
			.append("</report>")
			.toString();
	}

	/**
	 * Cobertura XML: per-class {@code <line hits=..>}; branch info rides the first
	 * covered line.
	 */
	private static String cobertura(List<FileCov> files) {
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><coverage line-rate=\"0.0\"><packages>");
		String lastPkg = null;
		for (FileCov f : files) {
			if (!f.pkg().equals(lastPkg)) {
				if (lastPkg != null) {
					sb.append("</classes></package>");
				}
				sb.append("<package name=\"").append(f.pkg()).append("\"><classes>");
				lastPkg = f.pkg();
			}
			sb.append("<class name=\"").append(f.file()).append("\" filename=\"").append(f.path()).append("\"><lines>");
			int branchTotal = f.branchCovered() + f.branchMissed();
			int n = 0;
			for (int i = 0; i < f.lineCovered(); i++) {
				n++;
				if (i == 0 && branchTotal > 0) {
					sb.append("<line number=\"")
						.append(n)
						.append("\" hits=\"1\" branch=\"true\" condition-coverage=\"x% (")
						.append(f.branchCovered())
						.append('/')
						.append(branchTotal)
						.append(")\"/>");
				}
				else {
					sb.append("<line number=\"").append(n).append("\" hits=\"1\"/>");
				}
			}
			for (int i = 0; i < f.lineMissed(); i++) {
				n++;
				sb.append("<line number=\"").append(n).append("\" hits=\"0\"/>");
			}
			sb.append("</lines></class>");
		}
		if (lastPkg != null) {
			sb.append("</classes></package>");
		}
		return sb.append("</packages></coverage>").toString();
	}

	/**
	 * LCOV .info: {@code DA:} per line, {@code BRDA:} per branch, one record per file.
	 */
	private static String lcov(List<FileCov> files) {
		StringBuilder sb = new StringBuilder();
		for (FileCov f : files) {
			sb.append("SF:").append(f.path()).append('\n');
			int n = 0;
			for (int i = 0; i < f.lineCovered(); i++) {
				sb.append("DA:").append(++n).append(",1\n");
			}
			for (int i = 0; i < f.lineMissed(); i++) {
				sb.append("DA:").append(++n).append(",0\n");
			}
			for (int i = 0; i < f.branchCovered(); i++) {
				sb.append("BRDA:1,0,").append(i).append(",1\n");
			}
			for (int i = 0; i < f.branchMissed(); i++) {
				sb.append("BRDA:1,0,").append(f.branchCovered() + i).append(",-\n");
			}
			sb.append("end_of_record\n");
		}
		return sb.toString();
	}

	private static String counter(String type, int covered, int missed) {
		return "<counter type=\"" + type + "\" missed=\"" + missed + "\" covered=\"" + covered + "\"/>";
	}

	private static String escape(String s) {
		return (s != null) ? s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				: "";
	}

	private record Case(String className, String name, boolean fail, String type, String message) {
	}

	private enum CovFmt {

		JACOCO, COBERTURA, LCOV

	}

	/** One source file's covered/missed line + branch counts. */
	private record FileCov(String pkg, String file, int lineCovered, int lineMissed, int branchCovered,
			int branchMissed) {

		String path() {
			return pkg + '/' + file;
		}

	}

}
