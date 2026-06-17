package org.alexmond.unitrack.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Applying the plugin registers the unitrack tasks (TestKit functional test). */
class UnitrackPluginFunctionalTest {

	@TempDir
	private File projectDir;

	private void write(String name, String content) throws IOException {
		Files.writeString(new File(this.projectDir, name).toPath(), content, StandardCharsets.UTF_8);
	}

	@Test
	void registersUnitrackTasks() throws IOException {
		write("settings.gradle", "rootProject.name = 'sample'");
		write("build.gradle", "plugins { id 'org.alexmond.unitrack' }");

		BuildResult result = GradleRunner.create()
			.withProjectDir(this.projectDir)
			.withArguments("tasks", "--group", "unitrack")
			.withPluginClasspath()
			.build();

		assertThat(result.getOutput()).contains("unitrackUpload").contains("unitrackGate");
	}

}
