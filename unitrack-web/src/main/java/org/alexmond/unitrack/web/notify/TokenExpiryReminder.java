package org.alexmond.unitrack.web.notify;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.ApiToken;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.repository.ApiTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job that emails token owners when an API token is within {@value #WINDOW_DAYS}
 * days of expiring, so it can be rotated before it stops working. Each token reminds at
 * most once (tracked via {@code expiryRemindedAt}). Best-effort and a no-op when
 * notifications are off.
 */
@Component
@RequiredArgsConstructor
public class TokenExpiryReminder {

	static final int WINDOW_DAYS = 7;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
		.withZone(ZoneOffset.UTC);

	private static final Logger log = LoggerFactory.getLogger(TokenExpiryReminder.class);

	private final ApiTokenRepository tokens;

	private final NotificationService notifications;

	/** Runs daily (override with {@code unitrack.notifications.token-expiry-cron}). */
	@Scheduled(cron = "${unitrack.notifications.token-expiry-cron:0 0 8 * * *}")
	public void run() {
		int sent = sendDueReminders(Instant.now());
		if (sent > 0) {
			log.info("Sent {} API-token expiry reminder(s)", sent);
		}
	}

	/**
	 * Emails owners of tokens expiring within the window; returns how many emails were
	 * sent.
	 */
	@Transactional
	public int sendDueReminders(Instant now) {
		if (!this.notifications.enabled()) {
			return 0;
		}
		Instant horizon = now.plus(WINDOW_DAYS, ChronoUnit.DAYS);
		List<ApiToken> due = this.tokens.findByRevokedFalseAndExpiryRemindedAtIsNullAndExpiresAtBetween(now, horizon);
		int sent = 0;
		for (ApiToken token : due) {
			User owner = token.getUser();
			String email = (owner != null) ? owner.getEmail() : null;
			if (owner != null && owner.isNotifyTokenExpiry() && email != null && !email.isBlank()) {
				this.notifications.send(email, subject(token), body(token));
				sent++;
			}
			token.setExpiryRemindedAt(now);
			this.tokens.save(token);
		}
		return sent;
	}

	private static String subject(ApiToken token) {
		return "⏳ UniTrack: API token '" + token.getName() + "' is expiring soon";
	}

	private String body(ApiToken token) {
		return "<h2>API token expiring soon</h2><p>Your UniTrack API token <strong>" + escape(token.getName())
				+ "</strong> (<code>" + escape(token.getPrefix()) + "…</code>) expires on <strong>"
				+ DATE.format(token.getExpiresAt()) + "</strong>.</p>" + "<p>Create a replacement on your <a href=\""
				+ this.notifications.link("/profile")
				+ "\">profile page</a> before it expires to avoid CI interruptions.</p>";
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
