package org.alexmond.unitrack.gradle;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures the {@code unitrack {}} block: the server URL/token, optional project name and
 * commit, and report globs (defaulting to Gradle's conventional test-result/JaCoCo locations).
 */
public class UnitrackExtension {

	private String url = System.getenv("UNITRACK_URL");

	private String token = System.getenv("UNITRACK_TOKEN");

	private String projectName;

	private String commit;

	private List<String> junit = new ArrayList<>();

	private List<String> jacoco = new ArrayList<>();

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getToken() {
		return this.token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getProjectName() {
		return this.projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getCommit() {
		return this.commit;
	}

	public void setCommit(String commit) {
		this.commit = commit;
	}

	public List<String> getJunit() {
		return this.junit;
	}

	public void setJunit(List<String> junit) {
		this.junit = junit;
	}

	public List<String> getJacoco() {
		return this.jacoco;
	}

	public void setJacoco(List<String> jacoco) {
		this.jacoco = jacoco;
	}

}
