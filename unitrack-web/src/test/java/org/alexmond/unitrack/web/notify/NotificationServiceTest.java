package org.alexmond.unitrack.web.notify;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceTest {

	private NotificationProperties props(boolean enabled) {
		NotificationProperties p = new NotificationProperties();
		p.setEnabled(enabled);
		p.setFromAddress("unitrack@example");
		p.setBaseUrl("https://unitrack.example/");
		return p;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		given(provider.getIfAvailable()).willReturn(sender);
		return provider;
	}

	private JavaMailSender realMessageSender() {
		JavaMailSender sender = mock(JavaMailSender.class);
		// createMimeMessage must return a usable MimeMessage for the helper to populate.
		given(sender.createMimeMessage()).willReturn(new JavaMailSenderImpl().createMimeMessage());
		return sender;
	}

	@Test
	void sendsHtmlEmailWhenEnabledAndConfigured() {
		JavaMailSender sender = realMessageSender();
		NotificationService svc = new NotificationService(props(true), provider(sender));

		assertThat(svc.enabled()).isTrue();
		svc.send("dev@example", "Gate failed", "<p>hi</p>");

		verify(sender).send(any(MimeMessage.class));
	}

	@Test
	void skipsWhenDisabled() {
		JavaMailSender sender = mock(JavaMailSender.class);
		NotificationService svc = new NotificationService(props(false), provider(sender));

		assertThat(svc.enabled()).isFalse();
		svc.send("dev@example", "s", "b");

		verifyNoInteractions(sender);
	}

	@Test
	void disabledWhenNoMailSenderConfigured() {
		NotificationService svc = new NotificationService(props(true), provider(null));

		assertThat(svc.enabled()).isFalse();
		assertThatCode(() -> svc.send("dev@example", "s", "b")).doesNotThrowAnyException();
	}

	@Test
	void skipsBlankRecipient() {
		JavaMailSender sender = mock(JavaMailSender.class);
		NotificationService svc = new NotificationService(props(true), provider(sender));

		svc.send("  ", "s", "b");

		verifyNoInteractions(sender);
	}

	@Test
	void swallowsSendFailures() {
		JavaMailSender sender = realMessageSender();
		willThrow(new MailSendException("smtp down")).given(sender).send(any(MimeMessage.class));
		NotificationService svc = new NotificationService(props(true), provider(sender));

		assertThatCode(() -> svc.send("dev@example", "s", "b")).doesNotThrowAnyException();
	}

	@Test
	void linkBuildsAbsoluteUrlTrimmingTrailingSlash() {
		NotificationService svc = new NotificationService(props(true), provider(null));
		assertThat(svc.link("/runs/42")).isEqualTo("https://unitrack.example/runs/42");
	}

}
