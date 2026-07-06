package org.alexmond.unitrack.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over the UniTrack ingest + gate endpoints, with retry on transient
 * failures.
 */
@Component
class UploadClient {

	private static final Pattern RUN_ID = Pattern.compile("\"runId\"\\s*:\\s*(\\d+)");

	private static final Pattern PASSED = Pattern.compile("\"passed\"\\s*:\\s*(true|false)");

	private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"");

	private static final int MAX_CAUSE_DEPTH = 20;

	private final RestClient.Builder builder;

	private final RetryTemplate retry;

	UploadClient(RestClient.Builder builder, RetryTemplate retry) {
		this.builder = builder;
		this.retry = retry;
	}

	/**
	 * POSTs a multipart ingest; returns the created run id (or {@code null} if the server
	 * reported none).
	 */
	IngestResponse ingest(String baseUrl, String token, Map<String, String> headers, Map<String, String> fields,
			Map<String, List<Resource>> files) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		fields.forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				parts.add(k, v);
			}
		});
		files.forEach((field, list) -> list.forEach((r) -> parts.add(field, r)));
		return withRetry(baseUrl, () -> {
			String body = client(baseUrl, token, headers).post()
				.uri("/api/v1/ingest")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts)
				.retrieve()
				.body(String.class);
			return new IngestResponse(extractLong(body));
		});
	}

	/**
	 * Looks up the latest run's gate verdict by project + commit/branch.
	 * {@code found=false} on 404.
	 */
	GateResponse gate(String baseUrl, String token, Map<String, String> headers, String project, String commit,
			String branch, String flag) {
		return withRetry(baseUrl, () -> {
			try {
				String body = client(baseUrl, token, headers).get()
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
				throw ex;
			}
		});
	}

	/**
	 * Runs an HTTP call under the retry policy: transient failures
	 * ({@link RetryableUploadException}, raised on network errors and retryable 5xx) are
	 * re-attempted and, if they survive, become a {@link ExitCodes#TRANSPORT} error;
	 * other HTTP errors map to the right exit code immediately.
	 */
	private <T> T withRetry(String baseUrl, Supplier<T> call) {
		try {
			return this.retry.execute(() -> attempt(baseUrl, call));
		}
		catch (RetryException ex) {
			// A non-retryable HTTP error surfaced (mapped already), or retries were
			// exhausted.
			UploadException uploadFailure = findUploadException(ex);
			if (uploadFailure != null) {
				throw uploadFailure;
			}
			throw new UploadException(ExitCodes.TRANSPORT, rootCause(ex).getMessage(), ex);
		}
	}

	/**
	 * One HTTP attempt; classifies failures into retryable vs terminal for the retry
	 * policy.
	 */
	private <T> T attempt(String baseUrl, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (RestClientResponseException ex) {
			if (isRetryable(ex.getStatusCode())) {
				throw new RetryableUploadException("server returned HTTP " + ex.getStatusCode().value(), ex);
			}
			int code = ex.getStatusCode().is4xxClientError() ? ExitCodes.REJECTED : ExitCodes.TRANSPORT;
			throw new UploadException(code, "server returned HTTP " + ex.getStatusCode().value(), ex);
		}
		catch (ResourceAccessException ex) {
			throw new RetryableUploadException("could not reach " + baseUrl + " (" + ex.getMessage() + ")", ex);
		}
	}

	private static UploadException findUploadException(Throwable thrown) {
		Throwable current = thrown;
		for (int depth = 0; depth < MAX_CAUSE_DEPTH && current != null; depth++) {
			if (current instanceof UploadException uploadFailure) {
				return uploadFailure;
			}
			current = current.getCause();
		}
		return null;
	}

	private static Throwable rootCause(Throwable thrown) {
		Throwable current = thrown;
		for (int depth = 0; depth < MAX_CAUSE_DEPTH && current.getCause() != null; depth++) {
			current = current.getCause();
		}
		return current;
	}

	private static boolean isRetryable(HttpStatusCode status) {
		int code = status.value();
		return code == 429 || code == 502 || code == 503 || code == 504;
	}

	private RestClient client(String baseUrl, String token, Map<String, String> headers) {
		return this.builder.clone().baseUrl(baseUrl).defaultHeaders((h) -> {
			if (token != null && !token.isBlank()) {
				h.setBearerAuth(token);
			}
			if (headers != null) {
				headers.forEach((name, value) -> h.set(name, value));
			}
		}).build();
	}

	/**
	 * Parses {@code Name: Value} header arguments (curl's {@code -H} form) into an
	 * ordered map. Lets an upload carry extra headers such as a proxy/WAF's credentials —
	 * e.g. Cloudflare Access service-token headers ({@code CF-Access-Client-Id} /
	 * {@code CF-Access-Client-Secret}) when the server sits behind Zero Trust.
	 */
	static Map<String, String> parseHeaders(List<String> headerArgs) {
		Map<String, String> map = new LinkedHashMap<>();
		if (headerArgs != null) {
			for (String header : headerArgs) {
				if (header == null || header.indexOf(':') <= 0) {
					throw new IllegalArgumentException("Invalid --header '" + header + "': expected 'Name: Value'");
				}
				int colon = header.indexOf(':');
				map.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
			}
		}
		return map;
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
