package org.alexmond.unitrack.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
		// CSRF disabled: the API is token/stateless and the UI is an internal dashboard.
		// (Re-enable with per-form tokens when hardening — see the auth epic.)
		http.csrf(AbstractHttpConfigurer::disable)
			.addFilterBefore(apiTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests((auth) -> {
				auth.requestMatchers("/login", "/signup", "/status", "/css/**", "/actuator/**", "/error").permitAll();
				auth.requestMatchers("/profile/**", "/api/v1/me/**").authenticated();
				auth.requestMatchers("/projects/*/settings", "/projects/*/members", "/projects/*/members/**")
					.authenticated();
				if (props.isRequireIngestToken()) {
					auth.requestMatchers(HttpMethod.POST, "/api/v1/ingest").authenticated();
				}
				if (props.isOpenMode()) {
					auth.anyRequest().permitAll();
				}
				else {
					auth.anyRequest().authenticated();
				}
			})
			.exceptionHandling((ex) -> ex
				.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiPaths)
				.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
						AnyRequestMatcher.INSTANCE))
			.formLogin((form) -> form.loginPage("/login").defaultSuccessUrl("/profile", false).permitAll())
			.logout((logout) -> logout.logoutSuccessUrl("/login?logout").permitAll());
		return http.build();
	}

}
