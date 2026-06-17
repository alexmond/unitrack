package org.alexmond.unitrack.web.alert;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
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
		try {
			Notifications<AlertEvent> notifications = this.factory.create(urls);
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
		try {
			this.factory.create(List.of(url)).send(event, List.of());
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
		String tags = String.join(",", channel.tags()).toLowerCase(Locale.ROOT);
		if (channel.type() == AlertChannelType.SLACK) {
			return wrap("slack", channel.secret(), tags);
		}
		if (channel.type() == AlertChannelType.WEBHOOK) {
			String endpoint = (channel.secret() != null && !channel.secret().isBlank()) ? channel.secret()
					: channel.target();
			return wrap("webhook", endpoint, tags);
		}
		return null;
	}

	/**
	 * Turns an http(s) endpoint into a {@code <channel>://…} (or
	 * {@code <channel>+http://…}) URL.
	 */
	private static String wrap(String channel, String endpoint, String tags) {
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}
		String url;
		if (endpoint.startsWith("https://")) {
			url = channel + "://" + endpoint.substring("https://".length());
		}
		else if (endpoint.startsWith("http://")) {
			url = channel + "+http://" + endpoint.substring("http://".length());
		}
		else {
			url = channel + "://" + endpoint;
		}
		if (!tags.isBlank()) {
			url += (url.contains("?") ? "&" : "?") + "tags=" + tags;
		}
		return url;
	}

}
