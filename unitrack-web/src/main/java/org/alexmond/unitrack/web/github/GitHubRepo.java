package org.alexmond.unitrack.web.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.alexmond.unitrack.web.importing.ImportableRepo;

/**
 * A GitHub repository as returned by the REST API, trimmed to the fields the import flow
 * needs. Unknown JSON properties are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepo(String name, @JsonProperty("full_name") String fullName,
		@JsonProperty("html_url") String htmlUrl, @JsonProperty("default_branch") String defaultBranch,
		@JsonProperty("private") boolean isPrivate, String description) implements ImportableRepo {

	@Override
	public String webUrl() {
		return htmlUrl();
	}
}
