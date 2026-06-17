package org.alexmond.unitrack.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Exec;

/**
 * Registers {@code unitrackUpload} and {@code unitrackGate} tasks that run the shared
 * {@code unitrack-cli} engine ({@code java -jar} the resolved {@code exec} jar) — so the Gradle
 * path reuses the exact upload/auto-detect/retry/gate logic with no duplication. Configure via
 * the {@code unitrack { url = …; token = … }} extension. Report globs default to Gradle's
 * conventional {@code build/test-results} and {@code build/reports/jacoco} locations.
 */
public class UnitrackPlugin implements Plugin<Project> {

	/** The unitrack-cli version whose executable jar this plugin runs. */
	static final String CLI_VERSION = "0.1.0-SNAPSHOT";

	@Override
	public void apply(Project project) {
		UnitrackExtension ext = project.getExtensions().create("unitrack", UnitrackExtension.class);
		Configuration cli = project.getConfigurations().create("unitrackCli", (c) -> {
			c.setVisible(false);
			c.setCanBeConsumed(false);
		});
		project.getDependencies().add("unitrackCli", "org.alexmond:unitrack-cli:" + CLI_VERSION + ":exec");

		project.getTasks().register("unitrackUpload", Exec.class, (task) -> {
			task.setGroup("unitrack");
			task.setDescription("Upload test and coverage reports to UniTrack.");
			task.doFirst((t) -> ((Exec) t).commandLine(command(project, cli, ext, "upload")));
		});
		project.getTasks().register("unitrackGate", Exec.class, (task) -> {
			task.setGroup("unitrack");
			task.setDescription("Fail the build on a red UniTrack quality gate.");
			task.doFirst((t) -> ((Exec) t).commandLine(command(project, cli, ext, "gate")));
		});
	}

	private List<String> command(Project project, Configuration cli, UnitrackExtension ext, String sub) {
		File jar = cli.getSingleFile();
		List<String> cmd = new ArrayList<>(List.of("java", "-jar", jar.getAbsolutePath(), sub));
		if (ext.getUrl() != null && !ext.getUrl().isBlank()) {
			cmd.add("--url");
			cmd.add(ext.getUrl());
		}
		if (ext.getToken() != null && !ext.getToken().isBlank()) {
			cmd.add("--token");
			cmd.add(ext.getToken());
		}
		cmd.add("--project");
		cmd.add((ext.getProjectName() != null) ? ext.getProjectName() : project.getName());
		if (ext.getCommit() != null && !ext.getCommit().isBlank()) {
			cmd.add("--commit");
			cmd.add(ext.getCommit());
		}
		if ("upload".equals(sub)) {
			addGlobs(cmd, "--junit", junitGlobs(project, ext));
			addGlobs(cmd, "--jacoco", jacocoGlobs(project, ext));
		}
		return cmd;
	}

	private static void addGlobs(List<String> cmd, String flag, List<String> globs) {
		for (String glob : globs) {
			cmd.add(flag);
			cmd.add(glob);
		}
	}

	private List<String> junitGlobs(Project project, UnitrackExtension ext) {
		if (!ext.getJunit().isEmpty()) {
			return ext.getJunit();
		}
		return List.of(project.getProjectDir().getAbsolutePath() + "/**/build/test-results/**/*.xml");
	}

	private List<String> jacocoGlobs(Project project, UnitrackExtension ext) {
		if (!ext.getJacoco().isEmpty()) {
			return ext.getJacoco();
		}
		return List.of(project.getProjectDir().getAbsolutePath() + "/**/build/reports/jacoco/**/*.xml");
	}

}
