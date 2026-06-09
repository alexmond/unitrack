package org.alexmond.unitrack.cli;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Thin HTTP client over the UniTrack ingest + gate endpoints. */
@Component
class UploadClient {

	private static final Pattern RUN_ID = Pattern.compile("\"runId\"\\s*:\\s*(\\d+)");

	private static final Pattern PASSED = Pattern.compile("\"passed\"\\s*:\\s*(true|false)");

	private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"");

	private final RestClient.Builder builder;

	UploadClient(RestClient.Builder builder) {
		this.builder = builder;
	}

	/**
	 * POSTs a multipart ingest; returns the created run id (or {@code null} if the server
	 * reported none).
	 */
	IngestResponse ingest(String baseUrl, String token, Map<String, String> fields, Map<String, List<Resource>> files) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		fields.forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				parts.add(k, v);
			}
		});
		files.forEach((field, list) -> list.forEach((r) -> parts.add(field, r)));
		try {
			String body = client(baseUrl, token).post()
				.uri("/api/v1/ingest")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts)
				.retrieve()
				.body(String.class);
			return new IngestResponse(extractLong(body));
		}
		catch (RestClientResponseException ex) {
			int code = ex.getStatusCode().is4xxClientError() ? ExitCodes.REJECTED : ExitCodes.TRANSPORT;
			throw new UploadException(code, "server returned HTTP " + ex.getStatusCode().value(), ex);
		}
		catch (ResourceAccessException ex) {
			throw new UploadException(ExitCodes.TRANSPORT, "could not reach " + baseUrl + " (" + ex.getMessage() + ")",
					ex);
		}
	}

	/**
	 * Looks up the latest run's gate verdict by project + commit/branch.
	 * {@code found=false} on 404.
	 */
	GateResponse gate(String baseUrl, String token, String project, String commit, String branch, String flag) {
		try {
			String body = client(baseUrl, token).get()
				.uri((uri) -> uri.path("/api/v1/gate")
					.queryParam("project", project)
					.queryParamIfPresent("commit", Optional.ofNullable(blankToNull(commit)))
					.queryParamIfPresent("branch", Optional.ofNullable(blankToNull(branch)))
					.queryParamIfPresent("flag", Optional.ofNullable(blankToNull(flag)))
					.build())
				.retrieve()
				.body(String.class);
			return new GateResponse(true, extractBool(body), extractStatus(body));
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 404) {
				return new GateResponse(false, false, null);
			}
			throw new UploadException(ExitCodes.TRANSPORT, "server returned HTTP " + ex.getStatusCode().value(), ex);
		}
		catch (ResourceAccessException ex) {
			throw new UploadException(ExitCodes.TRANSPORT, "could not reach " + baseUrl + " (" + ex.getMessage() + ")",
					ex);
		}
	}

	private RestClient client(String baseUrl, String token) {
		return this.builder.clone().baseUrl(baseUrl).defaultHeaders((h) -> {
			if (token != null && !token.isBlank()) {
				h.setBearerAuth(token);
			}
		}).build();
	}

	private static Long extractLong(String body) {
		Matcher m = (body != null) ? RUN_ID.matcher(body) : null;
		return (m != null && m.find()) ? Long.valueOf(m.group(1)) : null;
	}

	private static boolean extractBool(String body) {
		Matcher m = (body != null) ? PASSED.matcher(body) : null;
		return m != null && m.find() && Boolean.parseBoolean(m.group(1));
	}

	private static String extractStatus(String body) {
		Matcher m = (body != null) ? STATUS.matcher(body) : null;
		return (m != null && m.find()) ? m.group(1) : null;
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

}
