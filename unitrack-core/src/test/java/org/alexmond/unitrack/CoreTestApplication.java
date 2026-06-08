package org.alexmond.unitrack;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for testing the core module against a real context
 * (real repositories + embedded H2), with no web layer and no mocks. Component-scans
 * {@code org.alexmond.unitrack} so all services, parsers and
 * {@code @ConfigurationProperties} are picked up.
 */
@SpringBootApplication
public class CoreTestApplication {

}
