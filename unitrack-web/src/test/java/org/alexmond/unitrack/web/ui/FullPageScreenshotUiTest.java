package org.alexmond.unitrack.web.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
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
 * Captures a full-page screenshot of every dashboard page in BOTH themes to
 * {@code target/screenshots/<page>-<theme>.png}. Used to bank baselines and diff
 * before/after UI migration PRs (e.g. the Bootstrap migration). Tagged {@code ui};
 * self-skips (build stays green) when no browser/driver is available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("ui")
class FullPageScreenshotUiTest {

	private static final int MAX_PAGE_HEIGHT = 4000;

	@LocalServerPort
	private int port;

	private static byte[] junit() {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"2\" failures=\"1\" errors=\"0\" "
				+ "skipped=\"0\" time=\"0.5\">"
				+ "<testcase name=\"a\" classname=\"com.x.G\" time=\"0.3\"><failure message=\"boom\" "
				+ "type=\"java.lang.AssertionError\">trace</failure></testcase>"
				+ "<testcase name=\"b\" classname=\"com.x.G\" time=\"0.2\"/></testsuite>")
			.getBytes();
	}

	private static final byte[] JACOCO = ("<?xml version=\"1.0\"?><report name=\"r\">"
			+ "<counter type=\"LINE\" missed=\"2\" covered=\"8\"/>"
			+ "<package name=\"p\"><sourcefile name=\"F.java\"><counter type=\"LINE\" missed=\"2\" covered=\"8\"/>"
			+ "</sourcefile></package></report>")
		.getBytes();

	private static final byte[] JTL = "timeStamp,elapsed,label,success\n1000,100,GET /a,true\n1100,300,GET /a,false\n"
		.getBytes();

	private static Resource named(byte[] data, String name) {
		return new ByteArrayResource(data) {
			@Override
			public String getFilename() {
				return name;
			}
		};
	}

	private long ingestRun() {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("project", "demo");
		form.add("branch", "main");
		form.add("commit", "abc1234");
		form.add("junit", named(junit(), "TEST-G.xml"));
		form.add("jacoco", named(JACOCO, "jacoco.xml"));
		String body = post(form);
		return ((Number) JsonPath.read(body, "$.runId")).longValue();
	}

	private long ingestPerf() {
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("project", "demo");
		form.add("branch", "main");
		form.add("commit", "abc1234");
		form.add("perf", named(JTL, "results.jtl"));
		return ((Number) JsonPath.read(post(form), "$.perfRunId")).longValue();
	}

	private String post(MultiValueMap<String, Object> form) {
		return RestClient.create()
			.post()
			.uri("http://localhost:" + this.port + "/api/v1/ingest")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(form)
			.retrieve()
			.body(String.class);
	}

	private WebDriver newDriver() {
		ChromeOptions opts = new ChromeOptions();
		opts.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
				"--window-size=1280,1000");
		try {
			return new ChromeDriver(opts);
		}
		catch (RuntimeException | Error ex) {
			Assumptions.abort("Chrome/driver unavailable: " + ex.getMessage());
			return null;
		}
	}

	private void login(WebDriver driver, String base) {
		driver.get(base + "/login");
		driver.findElement(By.name("username")).sendKeys("admin");
		driver.findElement(By.name("password")).sendKeys("testadmin");
		driver.findElement(By.cssSelector("button[type=submit]")).click();
		new WebDriverWait(driver, Duration.ofSeconds(10)).until((d) -> !d.getCurrentUrl().endsWith("/login"));
	}

	private void shoot(WebDriver driver, String url, String theme, Path dir, String name) throws Exception {
		driver.get(url);
		((JavascriptExecutor) driver).executeScript("localStorage.setItem('theme', arguments[0]);", theme);
		driver.get(url); // reload so the pre-paint script applies the theme
		new WebDriverWait(driver, Duration.ofSeconds(10))
			.until((d) -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		long height = ((Number) ((JavascriptExecutor) driver)
			.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)"))
			.longValue();
		driver.manage().window().setSize(new Dimension(1280, (int) Math.min(height + 80, MAX_PAGE_HEIGHT)));
		byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(dir.resolve(name + "-" + theme + ".png"), png);
	}

	@Test
	void capturesEveryPageInBothThemes() throws Exception {
		long runId = ingestRun();
		long perfRunId = ingestPerf();
		String base = "http://localhost:" + this.port;
		// projectId is 1 (single project seeded above); resolve defensively via the run
		// if needed.
		long projectId = 1;

		Map<String, String> routes = new LinkedHashMap<>();
		routes.put("index", base + "/");
		routes.put("project", base + "/projects/" + projectId);
		routes.put("flaky", base + "/projects/" + projectId + "/flaky");
		routes.put("timing", base + "/projects/" + projectId + "/performance");
		routes.put("load", base + "/projects/" + projectId + "/perf");
		routes.put("clusters", base + "/projects/" + projectId + "/clusters");
		routes.put("triage", base + "/projects/" + projectId + "/triage");
		routes.put("run", base + "/runs/" + runId);
		routes.put("perf-run", base + "/perf-runs/" + perfRunId);
		routes.put("login", base + "/login");
		// Auth-gated pages (captured after logging in).
		routes.put("settings", base + "/projects/" + projectId + "/settings");
		routes.put("members", base + "/projects/" + projectId + "/members");
		routes.put("profile", base + "/profile");

		Path dir = Path.of("target", "screenshots");
		Files.createDirectories(dir);

		WebDriver driver = newDriver();
		try {
			login(driver, base);
			for (Map.Entry<String, String> route : routes.entrySet()) {
				shoot(driver, route.getValue(), "dark", dir, route.getKey());
				shoot(driver, route.getValue(), "light", dir, route.getKey());
			}
			System.out.println("Full-page screenshots written to " + dir.toAbsolutePath());
		}
		finally {
			driver.quit();
		}

		assertThat(dir.resolve("project-dark.png")).exists();
		assertThat(dir.resolve("project-light.png")).exists();
	}

}
