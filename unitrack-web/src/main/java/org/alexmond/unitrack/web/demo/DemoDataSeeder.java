package org.alexmond.unitrack.web.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.alexmond.unitrack.ingest.PerfIngestService;
import org.alexmond.unitrack.repository.ProjectRepository;
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
	}

	private List<Case> withExtra(List<Case> base, Case... extra) {
		List<Case> all = new ArrayList<>(base);
		all.addAll(List.of(extra));
		return all;
	}

	private void ingest(String project, String repo, String branch, String flag, String commit, List<Case> cases,
			int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
		IngestRequest meta = new IngestRequest(project, repo, branch, flag, commit, null, "demo", null);
		String junitXml = junit(cases);
		String jacocoXml = jacoco(lineCovered, lineMissed, branchCovered, branchMissed);
		ingest.ingest(meta, List.of(supplier(junitXml)), List.of(supplier(jacocoXml)));
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
	 * (so the headline % is stable), with the same totals spread across a few
	 * packages/files — one well-covered, one poorly-covered — so the coverage page has a
	 * realistic breakdown.
	 */
	private static String jacoco(int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
		// package, file, covered-weight, missed-weight (weights sum to ~1.0 across the
		// set).
		String[][] files = { { "com/acme/service", "OrderService.java", "0.30", "0.08" },
				{ "com/acme/service", "PricingEngine.java", "0.12", "0.46" },
				{ "com/acme/web", "ApiController.java", "0.33", "0.21" },
				{ "com/acme/util", "JsonMapper.java", "0.25", "0.25" } };
		StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><report name=\"demo\">");
		String lastPkg = null;
		boolean pkgOpen = false;
		for (String[] f : files) {
			String pkg = f[0];
			if (!pkg.equals(lastPkg)) {
				if (pkgOpen) {
					sb.append("</package>");
				}
				sb.append("<package name=\"").append(pkg).append("\">");
				pkgOpen = true;
				lastPkg = pkg;
			}
			double wc = Double.parseDouble(f[2]);
			double wm = Double.parseDouble(f[3]);
			sb.append("<sourcefile name=\"")
				.append(f[1])
				.append("\">")
				.append(counter("LINE", (int) Math.round(lineCovered * wc), (int) Math.round(lineMissed * wm)))
				.append(counter("BRANCH", (int) Math.round(branchCovered * wc), (int) Math.round(branchMissed * wm)))
				.append("</sourcefile>");
		}
		if (pkgOpen) {
			sb.append("</package>");
		}
		return sb.append(counter("INSTRUCTION", lineCovered * 3, lineMissed * 3))
			.append(counter("LINE", lineCovered, lineMissed))
			.append(counter("BRANCH", branchCovered, branchMissed))
			.append(counter("METHOD", lineCovered / 20, lineMissed / 20))
			.append("</report>")
			.toString();
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

}
