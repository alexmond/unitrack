package org.alexmond.unitrack.web.webhook;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.report.BranchCleanupService;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

	private final BranchCleanupService cleanup = mock(BranchCleanupService.class);

	private final ProjectRepository projects = mock(ProjectRepository.class);

	private WebhookController controller(String secret) {
		WebhookProperties props = new WebhookProperties();
		props.setSecret(secret);
		return new WebhookController(props, cleanup, projects);
	}

	private static byte[] hmac(String secret, byte[] body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return mac.doFinal(body);
	}

	private static String sign(String secret, byte[] body) throws Exception {
		return "sha256=" + HexFormat.of().formatHex(hmac(secret, body));
	}

	private void stubProject() {
		Project p = mock(Project.class);
		given(p.getId()).willReturn(5L);
		given(p.getRepoUrl()).willReturn("https://github.com/acme/widget");
		given(projects.findAll()).willReturn(List.of(p));
	}

	@Test
	void githubBranchDeleteRemovesTheBranch() throws Exception {
		stubProject();
		given(cleanup.deleteBranch(5L, "feature/x")).willReturn(3);
		byte[] body = """
				{"ref":"feature/x","ref_type":"branch","repository":{"full_name":"acme/widget"}}"""
			.getBytes(StandardCharsets.UTF_8);

		ResponseEntity<String> r = controller("s3cr3t").github("delete", sign("s3cr3t", body), body);

		assertThat(r.getStatusCode().value()).isEqualTo(200);
		verify(cleanup).deleteBranch(5L, "feature/x");
	}

	@Test
	void githubRejectsABadSignature() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		ResponseEntity<String> r = controller("s3cr3t").github("delete", "sha256=deadbeef", body);
		assertThat(r.getStatusCode().value()).isEqualTo(401);
		verifyNoInteractions(cleanup);
	}

	@Test
	void disabledWhenNoSecretConfigured() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		ResponseEntity<String> r = controller("").github("delete", "x", body);
		assertThat(r.getStatusCode().value()).isEqualTo(404);
		verifyNoInteractions(cleanup);
	}

	@Test
	void githubIgnoresNonDeleteEvents() throws Exception {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		ResponseEntity<String> r = controller("s3cr3t").github("push", sign("s3cr3t", body), body);
		assertThat(r.getStatusCode().value()).isEqualTo(200);
		verifyNoInteractions(cleanup);
	}

	@Test
	void gitlabBranchDeleteRemovesTheBranch() {
		stubProject();
		given(cleanup.deleteBranch(5L, "feature/x")).willReturn(1);
		byte[] body = """
				{"ref":"refs/heads/feature/x","after":"0000000000000000000000000000000000000000",\
				"project":{"path_with_namespace":"acme/widget"}}""".getBytes(StandardCharsets.UTF_8);

		ResponseEntity<String> r = controller("tok").gitlab("tok", body);

		assertThat(r.getStatusCode().value()).isEqualTo(200);
		verify(cleanup).deleteBranch(5L, "feature/x");
	}

	@Test
	void gitlabRejectsABadToken() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		ResponseEntity<String> r = controller("tok").gitlab("wrong", body);
		assertThat(r.getStatusCode().value()).isEqualTo(401);
		verifyNoInteractions(cleanup);
	}

	@Test
	void ownerRepoNormalizesUrls() {
		assertThat(WebhookController.ownerRepo("git@github.com:acme/widget.git")).isEqualTo("acme/widget");
		assertThat(WebhookController.ownerRepo("https://gitlab.com/acme/widget/")).isEqualTo("acme/widget");
		assertThat(WebhookController.ownerRepo("acme/widget")).isEqualTo("acme/widget");
	}

}
