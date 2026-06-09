package org.alexmond.unitrack.web.notify;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Email-notification settings ({@code unitrack.notifications.*}). */
@Component
@ConfigurationProperties(prefix = "unitrack.notifications")
@Getter
@Setter
public class NotificationProperties {

	/** Master switch. Off by default, so no mail is sent until SMTP is configured. */
	private boolean enabled;

	/** From address for outgoing notifications. */
	private String fromAddress = "unitrack@localhost";

	/**
	 * Base URL used to build absolute links in emails (e.g.
	 * {@code https://unitrack.example}).
	 */
	private String baseUrl = "http://localhost:8080";

}
