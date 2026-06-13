package org.alexmond.unitrack.web.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Human-readable system status page — a rendered view of the actuator health endpoint
 * (overall UP/DOWN, per-component health, build/version and uptime) instead of raw JSON.
 */
@Controller
@RequiredArgsConstructor
public class StatusController {

	private final HealthEndpoint health;

	private final ObjectProvider<BuildProperties> buildProperties;

	private final ObjectProvider<GitProperties> gitProperties;

	private final Environment environment;

	@GetMapping("/status")
	public String status(Model model) {
		HealthDescriptor descriptor = health.health();
		model.addAttribute("overall", descriptor.getStatus().getCode());
		model.addAttribute("components", components(descriptor));

		RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
		model.addAttribute("uptime", formatUptime(runtime.getUptime()));
		model.addAttribute("started", Instant.ofEpochMilli(runtime.getStartTime()));
		model.addAttribute("javaVersion", System.getProperty("java.version"));
		model.addAttribute("profiles", environment.getActiveProfiles());

		BuildProperties build = buildProperties.getIfAvailable();
		model.addAttribute("version", (build != null) ? build.getVersion() : null);
		model.addAttribute("buildTime", (build != null) ? build.getTime() : null);
		GitProperties git = gitProperties.getIfAvailable();
		model.addAttribute("commit", (git != null) ? git.getShortCommitId() : null);
		model.addAttribute("branch", (git != null) ? git.getBranch() : null);
		return "status";
	}

	/** Flatten the aggregated health into rows the template can render. */
	private List<Map<String, Object>> components(HealthDescriptor descriptor) {
		List<Map<String, Object>> rows = new ArrayList<>();
		if (descriptor instanceof CompositeHealthDescriptor composite) {
			composite.getComponents().forEach((name, child) -> {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("name", name);
				row.put("status", child.getStatus().getCode());
				row.put("details",
						(child instanceof IndicatedHealthDescriptor indicated) ? indicated.getDetails() : Map.of());
				rows.add(row);
			});
		}
		return rows;
	}

	private static String formatUptime(long millis) {
		long seconds = millis / 1000;
		long days = seconds / 86_400;
		long hours = (seconds % 86_400) / 3600;
		long minutes = (seconds % 3600) / 60;
		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append(days).append("d ");
		}
		if (days > 0 || hours > 0) {
			sb.append(hours).append("h ");
		}
		sb.append(minutes).append('m');
		return sb.toString();
	}

}
