package org.alexmond.unitrack.web;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Thymeleaf layout dialect so pages can decorate the shared
 * {@code layout.html}.
 */
@Configuration
public class WebLayoutConfig {

	@Bean
	public LayoutDialect layoutDialect() {
		return new LayoutDialect();
	}

}
