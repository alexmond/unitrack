package org.alexmond.unitrack.web.notify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.alexmond.unitrack.domain.ApiToken;
import org.alexmond.unitrack.domain.Role;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.ApiTokenRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TokenExpiryReminderTest {

	private final ApiTokenRepository tokens = mock(ApiTokenRepository.class);

	private final NotificationService notifications = mock(NotificationService.class);

	private final TokenExpiryReminder reminder = new TokenExpiryReminder(tokens, notifications);

	private static ApiToken token(String name, String email, Instant expiresAt) {
		User owner = new User("user-" + name, name, email, "hash", Role.USER);
		return new ApiToken(owner, name, "hash-" + name, "ut_" + name, expiresAt,
				org.alexmond.unitrack.domain.TokenScope.FULL);
	}

	@Test
	void remindsOwnersWithAnAddressAndMarksEveryTokenReminded() {
		Instant now = Instant.now();
		given(notifications.enabled()).willReturn(true);
		given(notifications.link(anyString())).willReturn("https://unitrack.example/profile");
		ApiToken withEmail = token("ci", "dev@example", now.plus(3, ChronoUnit.DAYS));
		ApiToken noEmail = token("legacy", null, now.plus(1, ChronoUnit.DAYS));
		given(tokens.findByRevokedFalseAndExpiryRemindedAtIsNullAndExpiresAtBetween(eq(now), any()))
			.willReturn(List.of(withEmail, noEmail));

		int sent = reminder.sendDueReminders(now);

		assertThat(sent).isEqualTo(1);
		verify(notifications).send(eq("dev@example"), contains("expiring soon"), anyString());
		verify(notifications, never()).send(eq(null), anyString(), anyString());
		// Both tokens are marked reminded so they never fire again, even the one we
		// couldn't email.
		assertThat(withEmail.getExpiryRemindedAt()).isEqualTo(now);
		assertThat(noEmail.getExpiryRemindedAt()).isEqualTo(now);
		verify(tokens, times(2)).save(any(ApiToken.class));
	}

	@Test
	void respectsOptOutButStillMarksTokenReminded() {
		Instant now = Instant.now();
		given(notifications.enabled()).willReturn(true);
		ApiToken optedOut = token("ci", "dev@example", now.plus(2, ChronoUnit.DAYS));
		optedOut.getUser().setNotifyTokenExpiry(false);
		given(tokens.findByRevokedFalseAndExpiryRemindedAtIsNullAndExpiresAtBetween(eq(now), any()))
			.willReturn(List.of(optedOut));

		int sent = reminder.sendDueReminders(now);

		assertThat(sent).isZero();
		verify(notifications, never()).send(anyString(), anyString(), anyString());
		assertThat(optedOut.getExpiryRemindedAt()).isEqualTo(now);
		verify(tokens).save(optedOut);
	}

	@Test
	void noOpAndNoQueryWhenNotificationsDisabled() {
		given(notifications.enabled()).willReturn(false);
		reminder.run(); // also exercises the scheduled entry point
		assertThat(reminder.sendDueReminders(Instant.now())).isZero();
		verifyNoInteractions(tokens);
	}

}
