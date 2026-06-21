package org.alexmond.unitrack.web.notify;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.report.OwnershipService;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.TestRegressionResult;
import org.alexmond.unitrack.report.TestRegressionService;
import org.alexmond.unitrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes a run's <em>new</em> failures to the owners of the failing tests: each owner
 * gets a digest of just their newly-failing tests on the watched (base) branch. Turns
 * failure data into owned, actionable work (#225). Best-effort — never propagated to the
 * ingest path.
 */
@Component
@RequiredArgsConstructor
public class OwnerFailureNotifier {

	private static final Logger log = LoggerFactory.getLogger(OwnerFailureNotifier.class);

	private final NotificationService notifications;

	private final OwnershipService ownership;

	private final TestRegressionService regression;

	private final ProjectSettingsService settings;

	private final UserRepository users;

	/**
	 * Emails each owner their newly-failing tests; no-op unless on the base branch with
	 * regressions.
	 */
	public void notifyOwners(TestRun run) {
		if (!this.notifications.enabled()) {
			return;
		}
		try {
			Long projectId = run.getProject().getId();
			String watched = this.settings.gateConfig(projectId).baseBranch();
			if (watched != null && run.getBranch() != null && !watched.equals(run.getBranch())) {
				return; // only route on the watched/base branch
			}
			TestRegressionResult diff = this.regression.diff(run.getId()).orElse(null);
			if (diff == null || diff.newFailures().isEmpty()) {
				return;
			}
			Map<String, List<String>> byOwner = this.ownership.failuresByOwner(projectId, diff.newFailures());
			byOwner.forEach((owner, tests) -> {
				String to = resolveRecipient(owner);
				if (to != null) {
					String subject = String.format(Locale.ROOT, "❌ UniTrack: %d of your test(s) newly failed — %s",
							tests.size(), run.getProject().getName());
					this.notifications.send(to, subject, composeBody(run, owner, tests));
				}
			});
		}
		catch (RuntimeException ex) {
			log.warn("Owner-failure notification failed for run {}: {}", run.getId(), ex.getMessage());
		}
	}

	/**
	 * Resolves an owner string to an email: a UniTrack user (by username, honoring their
	 * failure- notify preference), else the owner string itself when it is an email;
	 * otherwise unroutable.
	 */
	private String resolveRecipient(String owner) {
		if (owner == null || owner.isBlank()) {
			return null;
		}
		String username = owner.startsWith("@") ? owner.substring(1) : owner;
		User user = this.users.findByUsername(username).orElse(null);
		if (user != null) {
			return (user.isNotifyGateFailure() && notBlank(user.getEmail())) ? user.getEmail() : null;
		}
		return (owner.contains("@") && owner.contains(".")) ? owner : null;
	}

	private String composeBody(TestRun run, String owner, List<String> tests) {
		StringBuilder sb = new StringBuilder("<h2>Your tests newly failed</h2>");
		sb.append(String.format(Locale.ROOT,
				"<p>Owner <strong>%s</strong> — <strong>%s</strong> run #%d on branch <code>%s</code>.</p>",
				escape(owner), escape(run.getProject().getName()), run.getId(), escape(run.getBranch())));
		sb.append("<ul>");
		tests.forEach((t) -> sb.append("<li>❌ <code>").append(escape(t)).append("</code></li>"));
		sb.append("</ul><p><a href=\"")
			.append(this.notifications.link("/runs/" + run.getId()))
			.append("\">View the run →</a></p>");
		return sb.toString();
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
