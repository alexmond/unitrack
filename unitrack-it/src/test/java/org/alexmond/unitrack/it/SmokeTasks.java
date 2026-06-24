package org.alexmond.unitrack.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Availability smoke against the active environment (the Spring profile picks the base
 * URL). Tagged {@code it} — excluded from a normal build; run with
 * {@code mvn -pl unitrack-it test -Dunitrack.it.excluded-groups= -Dspring.profiles.active=lab}.
 */
@SpringBootTest
@Tag("it")
class SmokeTasks {

	@Autowired
	private UniTrackApiClient api;

	@Test
	void publicEndpointsRespond() {
		assertThat(this.api.status("/actuator/health")).as("actuator health on %s", this.api.baseUrl()).isEqualTo(200);
		assertThat(this.api.status("/login")).as("login page").isEqualTo(200);
		// "/" renders in open mode (200) or redirects to /login in closed mode (302).
		assertThat(this.api.status("/")).as("home").isIn(200, 302);
	}

}
