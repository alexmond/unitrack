package org.alexmond.unitrack.web.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screenshot-tour deck capture (the {@code screenshot-tour} skill). Boots the app with
 * the built-in demo dataset and captures the dark-theme presentation deck to
 * {@code presentation/NN-<slug>.png}. Tagged {@code ui}; self-skips when no browser is
 * available. Run with: {@code ./mvnw -pl unitrack-web test -Dtest=ScreenshotTourTest
 * -DexcludedGroups=}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "unitrack.demo.enabled=true", "unitrack.security.open-mode=true",
				"spring.ai.anthropic.api-key=demo-stub-key" })
@Tag("ui")
@org.springframework.context.annotation.Import(ScreenshotTourTest.StubConfig.class)
class ScreenshotTourTest {

	private static final int MAX_PAGE_HEIGHT = 4000;

	private static final String SHOWCASE = "checkout-service";

	@LocalServerPort
	private int port;

	@Test
	void capturesTheTour() throws Exception {
		String base = "http://localhost:" + this.port;
		Path dir = Path.of("presentation");
		Files.createDirectories(dir);

		WebDriver driver = newDriver();
		try {
			login(driver, base);

			// 1. Board (all projects) + the broken-since rot signal (payments-gateway,
			// latest red).
			shoot(driver, base + "/", dir, "01-board");

			// Resolve the showcase project's id from the board (admin sees every
			// project).
			String projectUrl = base + "/projects/" + resolveProjectId(driver, base);

			// 2. Overview trend in "By run" mode — the showcase demo runs share a seed
			// timestamp, so even spacing reads better here than the proportional time
			// axis.
			shoot(driver, projectUrl, dir, "02-overview", "#trendModeToggle button[data-mode='run']");
			shoot(driver, projectUrl + "/coverage", dir, "03-coverage");
			shoot(driver, projectUrl + "/flaky", dir, "04-flaky");
			shoot(driver, projectUrl + "/clusters", dir, "05-clusters");

			// 6. AI root-cause — expand the cluster, click "Analyze with AI", capture the
			// card.
			shootAnalysis(driver, projectUrl + "/clusters", dir, "06-ai-rootcause");

			// 7. Load tests (perf) and 8. unit-test timing.
			shoot(driver, projectUrl + "/perf", dir, "07-load-tests");
			shoot(driver, projectUrl + "/performance", dir, "08-test-timing");

			// 9. Run detail — the newest run linked off the project overview.
			driver.get(projectUrl);
			String runUrl = driver.findElement(By.cssSelector("a[href*='/runs/']")).getAttribute("href");
			shoot(driver, runUrl, dir, "09-run-detail");

			// 11. Owners accountability board (10 = CLI gate, captured separately).
			shoot(driver, base + "/owners", dir, "11-owners");
		}
		finally {
			driver.quit();
		}

		assertThat(dir.resolve("01-board.png")).exists();
		assertThat(dir.resolve("02-overview.png")).exists();
	}

	private String resolveProjectId(WebDriver driver, String base) {
		driver.get(base + "/");
		List<WebElement> links = driver.findElements(By.cssSelector("a[href*='/projects/']"));
		String href = links.stream()
			.filter((e) -> SHOWCASE.equals(e.getText().trim()))
			.map((e) -> e.getAttribute("href"))
			.findFirst()
			.orElseGet(() -> links.isEmpty() ? null : links.get(0).getAttribute("href"));
		if (href == null) {
			throw new IllegalStateException("No projects on the board — is the demo dataset seeded?");
		}
		return href.replaceAll(".*/projects/(\\d+).*", "$1");
	}

	private WebDriver newDriver() {
		ChromeOptions opts = new ChromeOptions();
		opts.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
				"--hide-scrollbars", "--force-device-scale-factor=2", "--window-size=1440,900");
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

	private void shoot(WebDriver driver, String url, Path dir, String name) throws Exception {
		shoot(driver, url, dir, name, null);
	}

	/**
	 * Navigates, forces the dark theme, optionally clicks an element, full-page capture.
	 */
	private void shoot(WebDriver driver, String url, Path dir, String name, String clickAfter) throws Exception {
		driver.get(url);
		((JavascriptExecutor) driver).executeScript("localStorage.setItem('theme', 'dark');");
		driver.get(url); // reload so the pre-paint theme script applies
		new WebDriverWait(driver, Duration.ofSeconds(10))
			.until((d) -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		Thread.sleep(1200); // let Chart.js draw
		if (clickAfter != null) {
			try {
				driver.findElement(By.cssSelector(clickAfter)).click();
				Thread.sleep(700);
			}
			catch (RuntimeException ignored) {
				// element not present on this page — capture as-is
			}
		}
		long height = ((Number) ((JavascriptExecutor) driver)
			.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)"))
			.longValue();
		driver.manage().window().setSize(new Dimension(1440, (int) Math.min(height + 80, MAX_PAGE_HEIGHT)));
		Thread.sleep(300);
		byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(dir.resolve(name + ".png"), png);
	}

	/**
	 * Expands the cluster details, runs the click-to-run "Analyze with AI" action, waits
	 * for the htmx swap to render the analysis card (served by the stubbed model), then
	 * full-page captures.
	 */
	private void shootAnalysis(WebDriver driver, String url, Path dir, String name) throws Exception {
		driver.get(url);
		((JavascriptExecutor) driver).executeScript("localStorage.setItem('theme', 'dark');");
		driver.get(url);
		new WebDriverWait(driver, Duration.ofSeconds(10))
			.until((d) -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		// Open every <details> so the AI button inside the cluster body is
		// visible/clickable.
		((JavascriptExecutor) driver)
			.executeScript("document.querySelectorAll('details').forEach((e) => e.open = true);");
		Thread.sleep(500);
		driver.findElement(By.cssSelector(".ai-slot button")).click();
		// Wait for the analysis card to land in the first cluster's target.
		new WebDriverWait(driver, Duration.ofSeconds(20))
			.until((d) -> !d.findElement(By.id("ai-0")).getText().isBlank());
		Thread.sleep(600);
		long height = ((Number) ((JavascriptExecutor) driver)
			.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)"))
			.longValue();
		driver.manage().window().setSize(new Dimension(1440, (int) Math.min(height + 80, MAX_PAGE_HEIGHT)));
		Thread.sleep(300);
		Files.write(dir.resolve(name + ".png"), ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
	}

	/**
	 * Deterministic in-process model so the AI card renders a fixed answer — no key, no
	 * network.
	 */
	@org.springframework.boot.test.context.TestConfiguration
	static class StubConfig {

		@org.springframework.context.annotation.Bean
		@org.springframework.context.annotation.Primary
		org.springframework.ai.chat.model.ChatModel stubChatModel() {
			return new org.alexmond.unitrack.web.ai.support.StubChatModel("""
					{"rootCause":"The Cart passed to CheckoutService is null in both failing tests — it is never \
					initialized before the checkout flow runs, so Cart.total() throws a NullPointerException.",
					 "suggestion":"Construct or inject a Cart in the test fixture's @BeforeEach before exercising \
					checkout, and guard CheckoutService against a null cart.",
					 "confidence":0.84}""");
		}

	}

}
