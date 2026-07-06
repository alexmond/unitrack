package org.alexmond.unitrack.web.alert;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.alexmond.notify4j.ChannelCatalog;
import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.unitrack.domain.AlertChannelType;
import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delivers alert events through notify4j to a project's own channels (#238). For each
 * event it resolves the project's enabled channels, maps them to notify4j Apprise-style
 * URLs, builds a per-project {@link Notifications}, and sends with the event kind as the
 * route tag — so a channel tagged for that kind (or untagged) receives it. Best-effort:
 * any failure is logged, never propagated.
 */
@Component
@RequiredArgsConstructor
public class Notify4jAlertSink implements AlertSink {

	private static final Logger log = LoggerFactory.getLogger(Notify4jAlertSink.class);

	private final NotificationsFactory<AlertEvent> factory;

	private final AlertChannelService channels;

	@Override
	public void publish(AlertEvent event) {
		List<String> urls = this.channels.resolveEnabled(event.projectId())
			.stream()
			.map(Notify4jAlertSink::toUrl)
			.filter(Objects::nonNull)
			.toList();
		if (urls.isEmpty()) {
			return;
		}
		// Notifications is AutoCloseable (notify4j 0.8+): close it to release any
		// per-channel
		// resources (e.g. reminders). The shared delivery executor is caller-owned and is
		// NOT shut down by close(), so closing each per-event facade is safe.
		try (Notifications<AlertEvent> notifications = this.factory.create(urls)) {
			notifications.send(event, List.of(event.kind().name().toLowerCase(Locale.ROOT)));
		}
		catch (RuntimeException ex) {
			log.warn("notify4j delivery failed for project {} ({} channels)", event.projectId(), urls.size(), ex);
		}
	}

	/**
	 * Sends a synthetic test alert through one channel (ignores enabled/tags) so a user
	 * can verify wiring from the UI. Returns true if the channel maps to a deliverable
	 * notify4j URL; delivery itself stays best-effort.
	 */
	public boolean sendTest(AlertChannelService.Resolved channel, Long projectId, String projectName) {
		String url = toUrl(channel);
		if (url == null) {
			return false;
		}
		AlertEvent event = new AlertEvent(projectId, projectName, AlertKind.GATE_FAILED, null,
				"Test alert from UniTrack — channel '" + channel.label() + "' is wired up.");
		try (Notifications<AlertEvent> notifications = this.factory.create(List.of(url))) {
			notifications.send(event, List.of());
		}
		catch (RuntimeException ex) {
			log.warn("notify4j test delivery failed for channel {}", channel.label(), ex);
		}
		return true;
	}

	/**
	 * Maps a resolved channel to a notify4j URL, or null for kinds notify4j doesn't
	 * deliver per-URL (email is config-based; handled by the existing mail path until the
	 * routing slice).
	 */
	static String toUrl(AlertChannelService.Resolved channel) {
		// EMAIL is config-based (the mail path), not a per-URL notify4j channel.
		if (channel.type() == AlertChannelType.EMAIL) {
			return null;
		}
		String endpoint = (channel.secret() != null && !channel.secret().isBlank()) ? channel.secret()
				: channel.target();
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		// notify4j owns URL assembly: buildUrl strips a pasted http(s):// transport
		// (mapping
		// http:// to the +http form) and appends ?tags=, so UniTrack no longer hand-rolls
		// the Apprise-style URL. The scheme is the lowercased channel type; a type
		// notify4j
		// doesn't know is skipped rather than throwing (keeps delivery best-effort).
		String scheme = channel.type().name().toLowerCase(Locale.ROOT);
		ChannelCatalog catalog = ChannelCatalog.standard();
		if (catalog.describe(scheme).isEmpty()) {
			return null;
		}
		Set<String> tags = channel.tags()
			.stream()
			.map((t) -> t.toLowerCase(Locale.ROOT))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return catalog.buildUrl(scheme, Map.of("url", endpoint), tags, false);
	}

}
