package org.alexmond.unitrack.domain;

/**
 * Delivery target kind for a project {@link AlertChannel}. {@code EMAIL} carries a plain
 * address in {@code target}; every other kind is a notify4j "webhook-style" channel that
 * carries a pasted incoming-webhook URL in {@code secret} (stored encrypted) — SLACK,
 * TEAMS, DISCORD, MATTERMOST, ROCKETCHAT, GOOGLECHAT, and the generic WEBHOOK.
 * (Param-style notify4j channels — Telegram/ntfy/Gotify/PagerDuty/OpsGenie — need extra
 * fields and are not yet exposed.)
 */
public enum AlertChannelType {

	EMAIL, SLACK, WEBHOOK, TEAMS, DISCORD, MATTERMOST, ROCKETCHAT, GOOGLECHAT

}
