package org.alexmond.unitrack.maven;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Checks the project's UniTrack quality gate and fails the build when it is red, reusing
 * the {@code unitrack-cli} {@code gate} command (exit 1 = gate failed). Run after an
 * upload, e.g. {@code mvn unitrack:gate}.
 */
@Mojo(name = "gate", threadSafe = true)
public class GateMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	MavenProject project;

	@Parameter(property = "unitrack.url", defaultValue = "${env.UNITRACK_URL}")
	String url;

	@Parameter(property = "unitrack.token", defaultValue = "${env.UNITRACK_TOKEN}")
	String token;

	@Parameter(property = "unitrack.project")
	String projectName;

	@Parameter(property = "unitrack.commit", defaultValue = "${env.GIT_COMMIT}")
	String commit;

	@Parameter(property = "unitrack.skip", defaultValue = "false")
	boolean skip;

	@Override
	public void execute() throws MojoFailureException {
		if (this.skip) {
			getLog().info("UniTrack gate skipped (unitrack.skip=true).");
			return;
		}
		if (this.url == null || this.url.isBlank()) {
			getLog().warn("No UniTrack URL (set -Dunitrack.url or UNITRACK_URL) — skipping gate.");
			return;
		}
		List<String> args = new ArrayList<>();
		args.add("gate");
		args.add("--url");
		args.add(this.url);
		if (this.token != null && !this.token.isBlank()) {
			args.add("--token");
			args.add(this.token);
		}
		args.add("--project");
		args.add((this.projectName != null && !this.projectName.isBlank()) ? this.projectName
				: ((this.project.getName() != null) ? this.project.getName() : this.project.getArtifactId()));
		if (this.commit != null && !this.commit.isBlank()) {
			args.add("--commit");
			args.add(this.commit);
		}

		int code = CliRunner.run(args);
		if (code != 0) {
			throw new MojoFailureException("UniTrack quality gate failed (exit " + code + ").");
		}
	}

}
