package org.alexmond.unitrack.web.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
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
	}

	private void seedFrontend() {
		String name = "web-frontend";
		if (projects.findByName(name).isPresent()) {
			return;
		}
		List<Case> cases = List.of(new Case("com.web.HomeTest", "render", false, null, null),
				new Case("com.web.NavTest", "links", false, null, null),
				new Case("com.web.CartTest", "badge", false, null, null));
		ingest(name, "https://github.com/acme/web-frontend", "main", "frontend", "f1c0de1", cases, 850, 150, 75, 5);
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

	private static String jacoco(int lineCovered, int lineMissed, int branchCovered, int branchMissed) {
		return "<?xml version=\"1.0\"?><report name=\"demo\"><package name=\"com/acme\">"
				+ "<sourcefile name=\"Service.java\">" + counter("LINE", lineCovered, lineMissed)
				+ counter("BRANCH", branchCovered, branchMissed) + "</sourcefile></package>"
				+ counter("INSTRUCTION", lineCovered * 3, lineMissed * 3) + counter("LINE", lineCovered, lineMissed)
				+ counter("BRANCH", branchCovered, branchMissed) + counter("METHOD", lineCovered / 20, lineMissed / 20)
				+ "</report>";
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
