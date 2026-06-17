package org.alexmond.unitrack.web.alert;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.AlertChannel;
import org.alexmond.unitrack.domain.AlertChannelType;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.repository.AlertChannelRepository;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a project's alert channels. Secrets are encrypted on the way in (via
 * {@link SecretCipher}) and only ever decrypted by {@link #resolveEnabled(Long)} for the
 * delivery layer — list/read surfaces get a masked view, never the plaintext.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertChannelService {

	private final AlertChannelRepository channels;

	private final ProjectRepository projects;

	private final SecretCipher cipher;

	public List<AlertChannel> list(Long projectId) {
		return this.channels.findByProjectIdOrderByCreatedAtDesc(projectId);
	}

	@Transactional
	public AlertChannel add(Long projectId, AlertChannelType type, String label, String target, String secret,
			String tags) {
		Project project = this.projects.findById(projectId)
			.orElseThrow(() -> new IllegalArgumentException("No project with id " + projectId));
		return this.channels
			.save(new AlertChannel(project, type, label, target, this.cipher.encrypt(secret), normalizeTags(tags)));
	}

	@Transactional
	public void delete(Long channelId) {
		this.channels.deleteById(channelId);
	}

	@Transactional
	public void setEnabled(Long channelId, boolean enabled) {
		this.channels.findById(channelId).ifPresent((c) -> c.setEnabled(enabled));
	}

	public Optional<AlertChannel> find(Long channelId) {
		return this.channels.findById(channelId);
	}

	/**
	 * Resolves one channel (decrypted) regardless of enabled state — for the send-test.
	 */
	public Optional<Resolved> resolveOne(Long channelId) {
		return this.channels.findById(channelId)
			.map((c) -> new Resolved(c.getId(), c.getType(), c.getLabel(), c.getTarget(),
					this.cipher.decrypt(c.getSecret()), tagSet(c.getTags())));
	}

	/**
	 * Resolves a project's enabled channels with their secrets decrypted, for the
	 * delivery layer. Never expose this to a read/UI surface.
	 */
	public List<Resolved> resolveEnabled(Long projectId) {
		return this.channels.findByProjectIdAndEnabledTrueOrderByCreatedAtDesc(projectId)
			.stream()
			.map((c) -> new Resolved(c.getId(), c.getType(), c.getLabel(), c.getTarget(),
					this.cipher.decrypt(c.getSecret()), tagSet(c.getTags())))
			.toList();
	}

	/** Masks a stored secret for display: keeps a short prefix, hides the rest. */
	public static String mask(String secret) {
		if (secret == null || secret.isBlank()) {
			return "";
		}
		return "••••••••";
	}

	private static String normalizeTags(String tags) {
		if (tags == null || tags.isBlank()) {
			return "";
		}
		return Arrays.stream(tags.split(","))
			.map(String::trim)
			.filter((t) -> !t.isEmpty())
			.map((t) -> t.toUpperCase(java.util.Locale.ROOT))
			.distinct()
			.collect(Collectors.joining(","));
	}

	private static Set<String> tagSet(String tags) {
		if (tags == null || tags.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(tags.split(","))
			.map(String::trim)
			.filter((t) -> !t.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** An enabled channel with its secret decrypted, ready for the delivery layer. */
	public record Resolved(Long id, AlertChannelType type, String label, String target, String secret,
			Set<String> tags) {
	}

}
