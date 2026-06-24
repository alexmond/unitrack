package org.alexmond.unitrack.it;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import lombok.RequiredArgsConstructor;

/**
 * Minimal HTTP client for driving a UniTrack instance from tasks — replaces ad-hoc
 * {@code curl}. Reads the base URL (and optional token) from {@link ItProperties}, so the
 * same call hits whatever environment the active profile selected.
 */
@RequiredArgsConstructor
public class UniTrackApiClient {

	private final ItProperties props;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	/**
	 * GET {@code path} (relative to the base URL), returning the full response. Transient
	 * I/O failures (e.g. an intermittent reverse-proxy connection reset) are retried up
	 * to {@code maxAttempts} so a one-off blip doesn't fail a task.
	 */
	public HttpResponse<String> get(String path) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.props.getBaseUrl() + path))
			.timeout(Duration.ofSeconds(15))
			.GET();
		if (this.props.getToken() != null && !this.props.getToken().isBlank()) {
			builder.header("Authorization", "Bearer " + this.props.getToken());
		}
		HttpRequest request = builder.build();
		IOException last = null;
		for (int attempt = 1; attempt <= Math.max(1, this.props.getMaxAttempts()); attempt++) {
			try {
				return this.http.send(request, HttpResponse.BodyHandlers.ofString());
			}
			catch (IOException ex) {
				last = ex;
				pause(200L * attempt);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("GET " + path + " interrupted", ex);
			}
		}
		throw new IllegalStateException("GET " + path + " against " + this.props.getBaseUrl() + " failed after "
				+ this.props.getMaxAttempts() + " attempts", last);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/** HTTP status code for a GET of {@code path}. */
	public int status(String path) {
		return get(path).statusCode();
	}

	public String baseUrl() {
		return this.props.getBaseUrl();
	}

}
