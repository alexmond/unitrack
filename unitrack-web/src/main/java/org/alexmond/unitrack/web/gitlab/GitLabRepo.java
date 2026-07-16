package org.alexmond.unitrack.web.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.alexmond.unitrack.web.importing.ImportableRepo;

/**
 * A GitLab project as returned by {@code GET /projects}, trimmed to the fields the import
 * flow needs. GitLab calls repositories "projects" and namespaces them as
 * {@code group/subgroup/project} ({@code path_with_namespace}). Unknown JSON properties
 * are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabRepo(String name, @JsonProperty("path_with_namespace") String pathWithNamespace,
		@JsonProperty("web_url") String webUrl, @JsonProperty("default_branch") String defaultBranch, String visibility,
		String description) implements ImportableRepo {

	@Override
	public String fullName() {
		return this.pathWithNamespace;
	}

	/**
	 * GitLab visibility is {@code private|internal|public}; anything but public is
	 * "private".
	 */
	@Override
	public boolean isPrivate() {
		return !"public".equalsIgnoreCase(this.visibility);
	}
}
