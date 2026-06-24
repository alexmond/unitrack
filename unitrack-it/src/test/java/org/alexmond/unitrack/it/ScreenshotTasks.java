package org.alexmond.unitrack.it;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Captures key public pages of the active environment to {@code target/screenshots} for
 * docs / visual review. Self-skips when no browser is available so a tagged run on a
 * headless host stays green. Authenticated pages need a login step — a follow-up.
 */
@SpringBootTest
@Tag("it")
class ScreenshotTasks {

	private static final Logger log = LoggerFactory.getLogger(ScreenshotTasks.class);

	@Autowired
	private Screenshots screenshots;

	@Test
	void capturePublicPages() throws Exception {
		assumeTrue(this.screenshots.available(), "no browser/driver — skipping screenshots");
		Map<String, String> pages = new LinkedHashMap<>();
		pages.put("/login", "login");
		pages.put("/status", "status");
		for (Map.Entry<String, String> page : pages.entrySet()) {
			Path out = this.screenshots.capture(page.getKey(), page.getValue());
			log.info("captured {} -> {}", page.getKey(), out);
		}
	}

}
