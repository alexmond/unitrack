package org.alexmond.unitrack.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
 * header).
 */
@Component
@RequiredArgsConstructor
public class ApiTokenAuthFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final ApiTokenService tokens;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String raw = extractToken(request);
			if (raw != null) {
				tokens.authenticate(raw).ifPresent((user) -> {
					var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
					var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
					SecurityContextHolder.getContext().setAuthentication(auth);
				});
			}
		}
		chain.doFilter(request, response);
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
