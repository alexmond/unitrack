package org.alexmond.unitrack.web.ui;

import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser-level smoke test: ingests runs, loads the performance page in headless Chrome,
 * and asserts the trend chart parsed its data into real arrays. This catches render-time
 * regressions (e.g. JSON passed to Chart.js as a string) that API/MockMvc tests cannot
 * see.
 *
 * <p>
 * Tagged {@code ui}; self-skips (test aborted, build stays green) when no browser/driver
 * is available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("ui")
class ChartRenderingUiTest {

	@LocalServerPort
	private int port;

	private static byte[] junit(double a, double b) {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"2\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"" + (a + b) + "\">" + "<testcase name=\"a\" classname=\"com.x.G\" time=\"" + a
				+ "\"/>" + "<testcase name=\"b\" classname=\"com.x.G\" time=\"" + b + "\"/></testsuite>")
			.getBytes();
	}

	private static final byte[] JACOCO = ("<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<counter type=\"LINE\" missed=\"2\" covered=\"8\"/>"
			+ "<package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\"2\" covered=\"8\"/>"
			+ "</sourcefile></package></report>")
		.getBytes();

	private static Resource named(byte[] data, String name) {
		return new ByteArrayResource(data) {
			@Override
			public String getFilename() {
				return name;
			}
		};
	}

	private long ingest(String project, String commit, double a, double b) {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("project", project);
		form.add("branch", "main");
		form.add("commit", commit);
		form.add("junit", named(junit(a, b), "TEST-G.xml"));
		form.add("jacoco", named(JACOCO, "jacoco.xml"));
		String body = RestClient.create()
			.post()
			.uri("http://localhost:" + this.port + "/api/v1/ingest")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(form)
			.retrieve()
			.body(String.class);
		return ((Number) JsonPath.read(body, "$.projectId")).longValue();
	}

	private WebDriver newDriver() {
		ChromeOptions opts = new ChromeOptions();
		opts.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
				"--window-size=1440,1100");
		try {
			return new ChromeDriver(opts);
		}
		catch (RuntimeException | Error ex) {
			Assumptions.abort("Chrome/driver unavailable: " + ex.getMessage());
			return null; // unreachable: abort throws
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void performanceChartParsesRealDataNotJsonStringChars() {
		long projectId = ingest("ui-chart", "abc1234", 0.30, 0.20);
		ingest("ui-chart", "def5678", 0.40, 0.10);

		WebDriver driver = newDriver();
		try {
			driver.get("http://localhost:" + this.port + "/projects/" + projectId + "/performance");
			JavascriptExecutor js = (JavascriptExecutor) driver;

			// Wait for Chart.js to register the suite-time chart with at least one label.
			new WebDriverWait(driver, Duration.ofSeconds(15)).until((d) -> Boolean.TRUE
				.equals(((JavascriptExecutor) d).executeScript("return !!(window.Chart && Chart.getChart('suiteChart')"
						+ " && Chart.getChart('suiteChart').data.labels.length > 0);")));

			List<Object> labels = (List<Object>) js.executeScript("return Chart.getChart('suiteChart').data.labels;");
			List<Object> data = (List<Object>) js
				.executeScript("return Chart.getChart('suiteChart').data.datasets[0].data;");

			// Two runs -> exactly two labels/points. With the JSON-string bug the labels
			// would be the characters of the serialized array (many more than two).
			assertThat(labels).hasSize(2);
			assertThat(labels).allMatch((l) -> l instanceof String);
			assertThat(data).hasSize(2);
			assertThat(data).allMatch((v) -> v instanceof Number);
		}
		finally {
			driver.quit();
		}
	}

}
