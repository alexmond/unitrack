package org.alexmond.unitrack.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.TokenScope;
import org.alexmond.unitrack.web.account.ApiTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a personal API token (Bearer or X-UniTrack-Token
 * header). An INGEST-scoped token is a least-privilege credential: it may only be used on
 * {@code POST /api/v1/ingest}; presenting it anywhere else is rejected with 403, so a
 * leaked CI secret can't read private data or manage anything. An ACTION-scoped token is
 * likewise least-privilege but for the MCP transport ({@code /sse}, {@code /mcp/**}): it
 * authenticates as its owner there to drive the AI write tools, and is rejected
 * elsewhere.
 */
@Component
@RequiredArgsConstructor
public class ApiTokenAuthFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private static final String INGEST_PATH = "/api/v1/ingest";

	private final ApiTokenService tokens;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String raw = extractToken(request);
			if (raw != null) {
				ApiTokenService.Authenticated authed = tokens.authenticate(raw).orElse(null);
				if (authed != null) {
					if (authed.scope() == TokenScope.INGEST && !isIngestRequest(request)) {
						response.sendError(HttpServletResponse.SC_FORBIDDEN,
								"This token is scoped to ingest only (POST " + INGEST_PATH + ")");
						return;
					}
					if (authed.scope() == TokenScope.ACTION && !isMcpRequest(request)) {
						response.sendError(HttpServletResponse.SC_FORBIDDEN,
								"This token is scoped to MCP actions only (the /sse and /mcp endpoints)");
						return;
					}
					String role = switch (authed.scope()) {
						case INGEST -> "ROLE_INGEST";
						case ACTION -> "ROLE_ACTION";
						default -> "ROLE_" + authed.user().getRole().name();
					};
					var authorities = List.of(new SimpleGrantedAuthority(role));
					var auth = new UsernamePasswordAuthenticationToken(authed.user().getUsername(), null, authorities);
					SecurityContextHolder.getContext().setAuthentication(auth);
				}
			}
		}
		chain.doFilter(request, response);
	}

	private static boolean isIngestRequest(HttpServletRequest request) {
		return "POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().endsWith(INGEST_PATH);
	}

	private static boolean isMcpRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.equals("/sse") || uri.startsWith("/mcp/") || uri.equals("/mcp");
	}

	private static String extractToken(HttpServletRequest request) {
		String header = request.getHeader("X-UniTrack-Token");
		if (header != null && !header.isBlank()) {
			return header.trim();
		}
		String auth = request.getHeader("Authorization");
		return (auth != null && auth.startsWith(BEARER)) ? auth.substring(BEARER.length()).trim() : null;
	}

}
