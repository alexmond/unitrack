package org.alexmond.unitrack.maven;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Uploads this module's test + coverage reports to UniTrack, reusing the
 * {@code unitrack-cli} engine (auto-detection, retry, redaction). Bind to {@code verify}
 * (the default phase) or run {@code mvn unitrack:upload}. Reports default to the
 * conventional Surefire/Failsafe/JaCoCo globs under the project basedir; project name
 * defaults to the POM's name/artifactId.
 */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class UploadMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	MavenProject project;

	@Parameter(property = "unitrack.url", defaultValue = "${env.UNITRACK_URL}")
	String url;

	@Parameter(property = "unitrack.token", defaultValue = "${env.UNITRACK_TOKEN}")
	String token;

	@Parameter(property = "unitrack.project")
	String projectName;

	@Parameter(property = "unitrack.junit")
	List<String> junit;

	@Parameter(property = "unitrack.jacoco")
	List<String> jacoco;

	@Parameter(property = "unitrack.runKey")
	String runKey;

	@Parameter(property = "unitrack.softFail", defaultValue = "false")
	boolean softFail;

	@Parameter(property = "unitrack.skip", defaultValue = "false")
	boolean skip;

	@Override
	public void execute() throws MojoFailureException {
		if (this.skip) {
			getLog().info("UniTrack upload skipped (unitrack.skip=true).");
			return;
		}
		if (this.url == null || this.url.isBlank()) {
			getLog().warn("No UniTrack URL (set -Dunitrack.url or UNITRACK_URL) — skipping upload.");
			return;
		}
		List<String> args = new ArrayList<>();
		args.add("upload");
		args.add("--url");
		args.add(this.url);
		if (this.token != null && !this.token.isBlank()) {
			args.add("--token");
			args.add(this.token);
		}
		args.add("--project");
		args.add(resolveProjectName());
		if (this.runKey != null && !this.runKey.isBlank()) {
			args.add("--run-key");
			args.add(this.runKey);
		}
		addGlobs(args, "--junit", this.junit, defaultJunit());
		addGlobs(args, "--jacoco", this.jacoco, defaultJacoco());
		if (this.softFail) {
			args.add("--soft-fail");
		}

		int code = CliRunner.run(args);
		if (code != 0) {
			throw new MojoFailureException("UniTrack upload failed (exit " + code + ").");
		}
	}

	private static void addGlobs(List<String> args, String flag, List<String> provided, List<String> defaults) {
		List<String> globs = (provided != null && !provided.isEmpty()) ? provided : defaults;
		for (String glob : globs) {
			args.add(flag);
			args.add(glob);
		}
	}

	private String resolveProjectName() {
		if (this.projectName != null && !this.projectName.isBlank()) {
			return this.projectName;
		}
		return (this.project.getName() != null) ? this.project.getName() : this.project.getArtifactId();
	}

	private List<String> defaultJunit() {
		String base = this.project.getBasedir().getAbsolutePath();
		return List.of(base + "/**/target/surefire-reports/*.xml", base + "/**/target/failsafe-reports/*.xml");
	}

	private List<String> defaultJacoco() {
		String base = this.project.getBasedir().getAbsolutePath();
		return List.of(base + "/**/target/site/jacoco/jacoco.xml");
	}

}
