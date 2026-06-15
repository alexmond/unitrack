package org.alexmond.unitrack.web.alert;

import org.alexmond.unitrack.domain.AlertChannel;
import org.alexmond.unitrack.domain.AlertChannelType;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-project channels persist, secrets are encrypted at rest, and the resolver decrypts.
 */
@SpringBootTest
class AlertChannelIntegrationTest {

	@Autowired
	private AlertChannelService channels;

	@Autowired
	private ProjectRepository projects;

	@Autowired
	private SecretCipher cipher;

	private Long newProject(String name) {
		return this.projects.save(new Project(name, "https://github.com/acme/" + name)).getId();
	}

	@Test
	void secretIsEncryptedAtRestAndResolvedDecrypted() {
		Long pid = newProject("alerts-enc-demo");
		String webhookUrl = "https://hooks.slack.com/services/T000/B000/secrettoken";

		AlertChannel saved = this.channels.add(pid, AlertChannelType.SLACK, "#builds", null, webhookUrl,
				"GATE_FAILED, new_regression");

		// Stored ciphertext is not the plaintext...
		assertThat(saved.getSecret()).isNotNull().isNotEqualTo(webhookUrl);
		assertThat(this.cipher.decrypt(saved.getSecret())).isEqualTo(webhookUrl);
		// ...tags are normalized (trimmed + upper-cased).
		assertThat(saved.getTags()).isEqualTo("GATE_FAILED,NEW_REGRESSION");

		// The resolver hands the delivery layer the decrypted secret + tag set.
		assertThat(this.channels.resolveEnabled(pid)).singleElement().satisfies((r) -> {
			assertThat(r.secret()).isEqualTo(webhookUrl);
			assertThat(r.tags()).containsExactlyInAnyOrder("GATE_FAILED", "NEW_REGRESSION");
		});
	}

	@Test
	void listIsScopedToProjectAndDisabledChannelsAreNotResolved() {
		Long pid = newProject("alerts-scope-demo");
		Long other = newProject("alerts-other-demo");
		this.channels.add(other, AlertChannelType.WEBHOOK, "other", "https://x/y", "sig", "");

		AlertChannel ch = this.channels.add(pid, AlertChannelType.EMAIL, "ops", "ops@acme.test", null, "");
		assertThat(this.channels.list(pid)).extracting(AlertChannel::getId).containsExactly(ch.getId());

		this.channels.setEnabled(ch.getId(), false);
		assertThat(this.channels.resolveEnabled(pid)).isEmpty();
	}

	@Test
	void maskNeverRevealsTheSecret() {
		assertThat(AlertChannelService.mask("super-secret-token")).doesNotContain("secret");
		assertThat(AlertChannelService.mask(null)).isEmpty();
	}

}
