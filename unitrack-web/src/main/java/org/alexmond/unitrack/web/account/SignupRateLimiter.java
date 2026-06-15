package org.alexmond.unitrack.web.account;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.web.security.SecurityProperties;
import org.springframework.stereotype.Component;

/**
 * A small in-memory sliding-window rate limiter for self-service signup, keyed by client
 * IP, to blunt automated account creation. Single-instance only (state isn't shared
 * across replicas); good enough for the self-hosted deployment model.
 */
@Component
@RequiredArgsConstructor
public class SignupRateLimiter {

	private static final long WINDOW_MS = Duration.ofHours(1).toMillis();

	private final SecurityProperties props;

	private final Map<String, Deque<Long>> hits = new HashMap<>();

	private final ReentrantLock lock = new ReentrantLock();

	/** Records an attempt for the key; returns false if it exceeds the per-hour limit. */
	public boolean tryAcquire(String key) {
		int limit = props.getSignupRateLimitPerHour();
		if (limit <= 0) {
			return true;
		}
		long now = System.currentTimeMillis();
		this.lock.lock();
		try {
			Deque<Long> window = this.hits.computeIfAbsent(key, (k) -> new ArrayDeque<>());
			while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
				window.pollFirst();
			}
			if (window.size() >= limit) {
				return false;
			}
			window.addLast(now);
			return true;
		}
		finally {
			this.lock.unlock();
		}
	}

}
