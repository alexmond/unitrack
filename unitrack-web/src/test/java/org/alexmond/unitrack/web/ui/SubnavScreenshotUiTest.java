package org.alexmond.unitrack.web.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
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

/**
 * Captures screenshots of the project sub-nav (dark + light, overview + a sub-page active
 * state) to {@code target/screenshots/} for visual review of UI changes. Tagged
 * {@code ui}; self-skips (build stays green) when no browser/driver is available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("ui")
class SubnavScreenshotUiTest {

	@LocalServerPort
	private int port;

	private static byte[] junit(double a, double b) {
		return ("<?xml version=\"1.0\"?><testsuite name=\"com.x.G\" tests=\"2\" failures=\"0\" errors=\"0\" "
				+ "skipped=\"0\" time=\"" + (a + b) + "\">" + "<testcase name=\"a\" classname=\"com.x.G\" time=\"" + a
				+ "\"/>" + "<testcase name=\"b\" classname=\"com.x.G\" time=\"" + b + "\"/></testsuite>")
			.getBytes();
	}

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
				"--force-device-scale-factor=2", "--window-size=1280,900");
		try {
			return new ChromeDriver(opts);
		}
		catch (RuntimeException | Error ex) {
			Assumptions.abort("Chrome/driver unavailable: " + ex.getMessage());
			return null;
		}
	}

	private void shoot(WebDriver driver, String url, String theme, Path dir, String name) throws Exception {
		driver.get(url);
		((JavascriptExecutor) driver).executeScript("localStorage.setItem('theme', arguments[0]);", theme);
		driver.get(url); // reload so the pre-paint script applies the theme
		new WebDriverWait(driver, Duration.ofSeconds(10))
			.until((d) -> !d.findElements(By.cssSelector("nav.subnav")).isEmpty());
		WebElement nav = driver.findElement(By.cssSelector("nav.subnav"));
		byte[] png = nav.getScreenshotAs(OutputType.BYTES);
		Files.write(dir.resolve(name), png);
	}

	@Test
	void screenshotSubnav() throws Exception {
		long id = ingest("acme-web", "abc1234", 0.30, 0.20);
		ingest("acme-web", "def5678", 0.40, 0.10);
		Path dir = Path.of("target", "screenshots");
		Files.createDirectories(dir);
		String base = "http://localhost:" + this.port + "/projects/" + id;

		WebDriver driver = newDriver();
		try {
			shoot(driver, base, "dark", dir, "subnav-overview-dark.png");
			shoot(driver, base, "light", dir, "subnav-overview-light.png");
			shoot(driver, base + "/flaky", "dark", dir, "subnav-flaky-dark.png");
			System.out.println("Screenshots written to " + dir.toAbsolutePath());
		}
		finally {
			driver.quit();
		}
		// sanity: files exist
		org.assertj.core.api.Assertions.assertThat(new File(dir.toFile(), "subnav-overview-dark.png")).exists();
	}

}
