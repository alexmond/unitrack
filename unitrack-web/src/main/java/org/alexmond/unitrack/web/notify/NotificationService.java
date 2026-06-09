package org.alexmond.unitrack.web.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends notification emails. Best-effort: silently skips when disabled or no mail sender
 * is configured, and never propagates a send failure to the caller (e.g. the ingest
 * path).
 *
 * <p>
 * Depends on {@link JavaMailSender} via an {@link ObjectProvider} so the app still starts
 * when {@code spring.mail.*} is unset (Spring Boot only creates the sender bean when a
 * host is given).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationProperties props;

	private final ObjectProvider<JavaMailSender> mailSender;

	/**
	 * Whether notifications can actually be sent (enabled and a mail sender is wired).
	 */
	public boolean enabled() {
		return this.props.isEnabled() && this.mailSender.getIfAvailable() != null;
	}

	/**
	 * Sends one HTML email; no-op when disabled, unconfigured, or the recipient is blank.
	 */
	public void send(String to, String subject, String htmlBody) {
		if (!this.props.isEnabled() || to == null || to.isBlank()) {
			return;
		}
		JavaMailSender sender = this.mailSender.getIfAvailable();
		if (sender == null) {
			log.debug("Notifications enabled but no mail sender configured; skipping '{}'", subject);
			return;
		}
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(this.props.getFromAddress());
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
			sender.send(message);
			log.info("Sent notification '{}' to {}", subject, to);
		}
		catch (Exception ex) {
			log.warn("Failed to send notification '{}' to {}: {}", subject, to, ex.getMessage());
		}
	}

	/** Absolute link to a path on this server, e.g. {@code /runs/42}. */
	public String link(String path) {
		String base = this.props.getBaseUrl();
		String trimmed = (base != null && base.endsWith("/")) ? base.substring(0, base.length() - 1) : base;
		return trimmed + path;
	}

}
