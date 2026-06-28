package org.alexmond.unitrack.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * UI form login + personal-API-token auth. In open mode (default) all endpoints are
 * permitted, but {@code /profile} and {@code /api/v1/me} always require a principal.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final SecurityProperties props;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiTokenAuthFilter apiTokenAuthFilter)
			throws Exception {
		// Unauthenticated API calls get a 401 instead of a redirect to the login page.
		RequestMatcher apiPaths = (request) -> request.getRequestURI().startsWith("/api/");
		// MCP transport (SSE + JSON-RPC) — stateless, no browser form, so CSRF-exempt
		// like the API.
		RequestMatcher mcpPaths = (request) -> {
			String uri = request.getRequestURI();
			return uri.equals("/sse") || uri.startsWith("/mcp");
		};
		// CSRF is ON for the cookie-session UI (Thymeleaf injects per-form tokens); the
		// token-auth,
		// stateless API and the MCP transport are exempt (no ambient browser credentials
		// to forge).
		http.csrf((csrf) -> csrf.ignoringRequestMatchers(apiPaths, mcpPaths))
			.addFilterBefore(apiTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests((auth) -> {
				// Static assets are public — otherwise, in closed mode, the login page
				// can't load
				// its CSS/JS (unstyled form) and the blocked /js/live.js fetch becomes
				// the saved
				// post-login redirect target.
				auth.requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll();
				auth.requestMatchers("/login", "/signup", "/status", "/actuator/**", "/error").permitAll();
				// Badges are public assets (READMEs fetch them anonymously); the
				// controller
				// still 404s a private project so it can't be probed.
				auth.requestMatchers("/badge/**").permitAll();
				// Public share links: the token in the path is the capability, so the
				// read-only view is reachable without a session even in closed mode. The
				// token is unguessable and revocable; creating/revoking lives under
				// /runs/** and still requires write access.
				auth.requestMatchers(HttpMethod.GET, "/share/**").permitAll();
				auth.requestMatchers("/profile/**", "/api/v1/me/**").authenticated();
				// The audit log can name private projects/users — admin-only, even in
				// open
				// mode.
				auth.requestMatchers("/audit/**", "/api/v1/audit", "/api/v1/audit/**", "/ops", "/ops/**", "/ingest",
						"/api/v1/ingest-jobs")
					.hasRole("ADMIN");
				// A single ingest job is readable by an admin or its own uploader (CI
				// polling its async upload) — the owner check is enforced in the
				// controller; just require authentication here.
				auth.requestMatchers(HttpMethod.GET, "/api/v1/ingest-jobs/*").authenticated();
				// Provisioning projects from GitHub is a management action: always
				// require login.
				auth.requestMatchers("/import", "/import/**").authenticated();
				auth.requestMatchers("/projects/*/settings", "/projects/*/visibility", "/projects/*/members",
						"/projects/*/members/**", "/projects/*/alerts", "/projects/*/alerts/**")
					.authenticated();
				if (props.isRequireIngestToken()) {
					auth.requestMatchers(HttpMethod.POST, "/api/v1/ingest").authenticated();
				}
				if (props.isOpenMode()) {
					auth.anyRequest().permitAll();
				}
				else {
					// Closed mode is now "public-readable": anonymous visitors may READ,
					// and
					// per-project visibility is enforced in the controllers (PUBLIC
					// projects are
					// shown; PRIVATE ones 404 — see ProjectAccessService). All
					// writes/management
					// require a login (defense in depth on top of the controller checks).
					// Ingest
					// stays open for CI unless an ingest token is required (handled
					// above).
					if (!props.isRequireIngestToken()) {
						auth.requestMatchers(HttpMethod.POST, "/api/v1/ingest").permitAll();
					}
					auth.requestMatchers(HttpMethod.POST, "/**").authenticated();
					auth.requestMatchers(HttpMethod.PUT, "/**").authenticated();
					auth.requestMatchers(HttpMethod.DELETE, "/**").authenticated();
					auth.requestMatchers(HttpMethod.PATCH, "/**").authenticated();
					auth.anyRequest().permitAll();
				}
			})
			.exceptionHandling((ex) -> ex
				.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiPaths)
				.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
						AnyRequestMatcher.INSTANCE))
			.formLogin((form) -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
			.logout((logout) -> logout.logoutSuccessUrl("/login?logout").permitAll());
		return http.build();
	}

}
