package org.alexmond.unitrack.domain;

/**
 * Delivery target kind for a project {@link AlertChannel}. {@code EMAIL} carries a plain
 * address in {@code target}; {@code SLACK} and {@code WEBHOOK} carry a secret endpoint
 * (stored encrypted).
 */
public enum AlertChannelType {

	EMAIL, SLACK, WEBHOOK

}
