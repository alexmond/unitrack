package org.alexmond.unitrack.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures a page of the target instance to {@code screenshotDir} via headless Chrome
 * (driver resolved by Selenium Manager). {@link #available()} probes for a usable browser
 * so tasks can skip cleanly on a headless host instead of failing.
 */
@RequiredArgsConstructor
public class Screenshots {

	private static final Logger log = LoggerFactory.getLogger(Screenshots.class);

	private static final Dimension VIEWPORT = new Dimension(1440, 900);

	private final ItProperties props;

	/** True when a headless Chrome can be started (else tasks should skip). */
	public boolean available() {
		try {
			ChromeDriver driver = new ChromeDriver(options());
			driver.quit();
			return true;
		}
		catch (WebDriverException ex) {
			log.warn("No Chrome/driver available — screenshot tasks will be skipped: {}", ex.getMessage());
			return false;
		}
	}

	/**
	 * Navigate to {@code path} on the target instance and write a PNG named {@code name}.
	 */
	public Path capture(String path, String name) throws IOException {
		ChromeDriver driver = new ChromeDriver(options());
		try {
			driver.manage().window().setSize(VIEWPORT);
			driver.get(this.props.getBaseUrl() + path);
			byte[] png = driver.getScreenshotAs(OutputType.BYTES);
			Files.createDirectories(this.props.getScreenshotDir());
			Path out = this.props.getScreenshotDir().resolve(name + ".png");
			Files.write(out, png);
			return out;
		}
		finally {
			driver.quit();
		}
	}

	private static ChromeOptions options() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage");
		return options;
	}

}
