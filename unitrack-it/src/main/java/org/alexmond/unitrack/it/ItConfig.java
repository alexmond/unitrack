package org.alexmond.unitrack.it;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * The integration-tasks application context. Intentionally tiny and <em>not</em> a web
 * app — it provides the building blocks ({@link UniTrackApiClient}, {@link Screenshots})
 * that tasks drive against a remote UniTrack selected by the active profile. Serves as
 * the {@code @SpringBootConfiguration} the task {@code @SpringBootTest}s bootstrap from.
 */
@SpringBootConfiguration
@EnableConfigurationProperties(ItProperties.class)
public class ItConfig {

	@Bean
	UniTrackApiClient uniTrackApiClient(ItProperties props) {
		return new UniTrackApiClient(props);
	}

	@Bean
	Screenshots screenshots(ItProperties props) {
		return new Screenshots(props);
	}

}
