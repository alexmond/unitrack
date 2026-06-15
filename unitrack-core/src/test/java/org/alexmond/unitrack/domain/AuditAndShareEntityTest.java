package org.alexmond.unitrack.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain-entity behavior for the audit + share-link records (exercised end-to-end in web).
 */
class AuditAndShareEntityTest {

	@Test
	void auditEntryExposesItsFields() {
		AuditEntry e = new AuditEntry("alice", "ACK_CLUSTER", "MCP", 5L, "acknowledged cluster X");
		assertThat(e.getActor()).isEqualTo("alice");
		assertThat(e.getAction()).isEqualTo("ACK_CLUSTER");
		assertThat(e.getSource()).isEqualTo("MCP");
		assertThat(e.getProjectId()).isEqualTo(5L);
		assertThat(e.getDetail()).isEqualTo("acknowledged cluster X");
		assertThat(e.getCreatedAt()).isNotNull();
	}

	@Test
	void alertEventCarriesItsFields() {
		AlertEvent e = new AlertEvent(3L, "proj", AlertKind.GATE_FAILED, 9L, "Quality gate failed");
		assertThat(e.projectId()).isEqualTo(3L);
		assertThat(e.projectName()).isEqualTo("proj");
		assertThat(e.kind()).isEqualTo(AlertKind.GATE_FAILED);
		assertThat(e.runId()).isEqualTo(9L);
		assertThat(e.message()).isEqualTo("Quality gate failed");
	}

	@Test
	void shareLinkIsActiveUntilRevoked() {
		ShareLink link = new ShareLink(null, "hash-value", "sh_abc12…", null);
		assertThat(link.getTokenHash()).isEqualTo("hash-value");
		assertThat(link.getPrefix()).isEqualTo("sh_abc12…");
		assertThat(link.getCreatedAt()).isNotNull();
		assertThat(link.isActive()).isTrue();

		Instant now = Instant.now();
		link.setLastUsedAt(now);
		assertThat(link.getLastUsedAt()).isEqualTo(now);

		link.setRevoked(true);
		assertThat(link.isActive()).isFalse();
	}

}
