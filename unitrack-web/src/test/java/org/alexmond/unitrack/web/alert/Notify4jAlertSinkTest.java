package org.alexmond.unitrack.web.alert;

import java.util.List;
import java.util.Set;

import org.alexmond.notify4j.Notifications;
import org.alexmond.notify4j.NotificationsFactory;
import org.alexmond.unitrack.domain.AlertChannelType;
import org.alexmond.unitrack.domain.AlertEvent;
import org.alexmond.unitrack.domain.AlertKind;
import org.alexmond.unitrack.web.alert.AlertChannelService.Resolved;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class Notify4jAlertSinkTest {

	@SuppressWarnings("unchecked")
	private final NotificationsFactory<AlertEvent> factory = mock(NotificationsFactory.class);

	private final AlertChannelService channels = mock(AlertChannelService.class);

	private final Notify4jAlertSink sink = new Notify4jAlertSink(this.factory, this.channels);

	private static final AlertEvent EVENT = new AlertEvent(5L, "proj", AlertKind.GATE_FAILED, 9L,
			"Quality gate failed");

	@Test
	void mapsChannelTypesToNotify4jUrls() {
		Resolved slack = new Resolved(1L, AlertChannelType.SLACK, "#b", null, "https://hooks.slack.com/services/T/B/X",
				Set.of("GATE_FAILED"));
		Resolved webhook = new Resolved(2L, AlertChannelType.WEBHOOK, "wh", null, "https://my-host/notify", Set.of());
		Resolved email = new Resolved(3L, AlertChannelType.EMAIL, "ops", "ops@x.test", null, Set.of());

		assertThat(Notify4jAlertSink.toUrl(slack)).isEqualTo("slack://hooks.slack.com/services/T/B/X?tags=gate_failed");
		assertThat(Notify4jAlertSink.toUrl(webhook)).isEqualTo("webhook://my-host/notify");
		assertThat(Notify4jAlertSink.toUrl(email)).isNull(); // email isn't a per-URL
																// channel
	}

	@Test
	void sendsThroughNotify4jWithKindAsRouteTag() {
		given(this.channels.resolveEnabled(5L)).willReturn(List.of(new Resolved(1L, AlertChannelType.SLACK, "#b", null,
				"https://hooks.slack.com/services/T/B/X", Set.of("GATE_FAILED"))));
		@SuppressWarnings("unchecked")
		Notifications<AlertEvent> notifications = mock(Notifications.class);
		given(this.factory.create(List.of("slack://hooks.slack.com/services/T/B/X?tags=gate_failed")))
			.willReturn(notifications);

		this.sink.publish(EVENT);

		verify(notifications).send(eq(EVENT), eq(List.of("gate_failed")));
	}

	@Test
	void doesNothingWhenProjectHasNoEnabledChannels() {
		given(this.channels.resolveEnabled(5L)).willReturn(List.of());

		this.sink.publish(EVENT);

		verifyNoInteractions(this.factory);
	}

}
