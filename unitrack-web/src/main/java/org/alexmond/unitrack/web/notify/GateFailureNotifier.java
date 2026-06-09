package org.alexmond.unitrack.web.notify;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.domain.User;
import org.alexmond.unitrack.report.ProjectSettingsService;
import org.alexmond.unitrack.report.QualityGateResult;
import org.alexmond.unitrack.repository.ProjectMembershipRepository;
import org.alexmond.unitrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emails a project's members when a run's quality gate fails on the watched (base)
 * branch. Best-effort: any failure is logged, never propagated to the ingest path.
 */
@Component
@RequiredArgsConstructor
public class GateFailureNotifier {

	private static final Logger log = LoggerFactory.getLogger(GateFailureNotifier.class);

	private final NotificationService notifications;

	private final ProjectMembershipRepository memberships;

	private final UserRepository users;

	private final ProjectSettingsService settings;

	/**
	 * Notifies project members if the gate failed on the watched branch; otherwise a
	 * no-op.
	 */
	public void notifyIfFailed(TestRun run, QualityGateResult gate) {
		if (gate == null || gate.passed() || !this.notifications.enabled()) {
			return;
		}
		try {
			Long projectId = run.getProject().getId();
			String watched = this.settings.gateConfig(projectId).baseBranch();
			if (watched != null && run.getBranch() != null && !watched.equals(run.getBranch())) {
				return; // only notify on the watched/base branch
			}
			List<String> recipients = recipients(projectId);
			if (recipients.isEmpty()) {
				return;
			}
			String subject = "❌ UniTrack: quality gate failed — " + run.getProject().getName();
			String body = composeBody(run, gate);
			recipients.forEach((to) -> this.notifications.send(to, subject, body));
		}
		catch (RuntimeException ex) {
			log.warn("Gate-failure notification failed for run {}: {}", run.getId(), ex.getMessage());
		}
	}

	private List<String> recipients(Long projectId) {
		return this.memberships.findByProjectIdOrderByRoleAscIdAsc(projectId)
			.stream()
			.map((m) -> this.users.findById(m.getUserId()).orElse(null))
			.filter(Objects::nonNull)
			.map(User::getEmail)
			.filter((e) -> e != null && !e.isBlank())
			.distinct()
			.toList();
	}

	private String composeBody(TestRun run, QualityGateResult gate) {
		StringBuilder sb = new StringBuilder("<h2>Quality gate failed</h2>");
		sb.append(String.format(Locale.ROOT, "<p><strong>%s</strong> — run #%d on branch <code>%s</code>",
				escape(run.getProject().getName()), run.getId(), escape(run.getBranch())));
		if (run.getShortSha() != null) {
			sb.append(" (").append(escape(run.getShortSha())).append(')');
		}
		sb.append("</p>");
		sb.append(String.format(Locale.ROOT, "<p>%d passed, %d failed, %d skipped of %d tests.</p>", run.getPassed(),
				run.getFailed() + run.getErrors(), run.getSkipped(), run.getTotalTests()));
		sb.append("<ul>");
		gate.rules()
			.stream()
			.filter((r) -> !r.passed())
			.forEach((r) -> sb.append("<li>❌ <strong>")
				.append(escape(r.name()))
				.append("</strong> — ")
				.append(escape(r.detail()))
				.append("</li>"));
		sb.append("</ul><p><a href=\"")
			.append(this.notifications.link("/runs/" + run.getId()))
			.append("\">View the run →</a></p>");
		return sb.toString();
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
