package org.alexmond.unitrack.web.importing;

/**
 * A source-control repository as surfaced by the "Import projects" flow, independent of
 * the hosting provider (GitHub, GitLab, …). The import UI and
 * {@code ProjectImportService} work against this so provider-specific record shapes
 * ({@code GitHubRepo}, {@code GitLabRepo}) plug in without duplicating the
 * pick-and-provision logic.
 */
public interface ImportableRepo {

	/** The short repository name — becomes the UniTrack project name. */
	String name();

	/** The fully-qualified path ({@code owner/repo} or {@code group/sub/project}). */
	String fullName();

	/** The browser URL of the repository — stored as the project's repo URL. */
	String webUrl();

	/** The repository's default branch, for display. */
	String defaultBranch();

	/** Whether the repository is private (drives the "private" badge). */
	boolean isPrivate();

	/** A short description, or null. */
	String description();

}
