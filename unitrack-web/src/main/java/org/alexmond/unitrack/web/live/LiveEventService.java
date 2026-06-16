package org.alexmond.unitrack.web.live;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.web.account.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registry of live SSE subscribers and the broadcast spine. Each subscriber is remembered
 * with the username it connected as; a run-ingested update is delivered only to
 * subscribers that may read the run's project (so a private project's activity never
 * leaks to anonymous or non-member streams). Delivery is best-effort — a dead connection
 * is dropped, never propagated.
 */
@Service
@RequiredArgsConstructor
public class LiveEventService {

	private static final Logger log = LoggerFactory.getLogger(LiveEventService.class);

	private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

	private static final long RECONNECT_MS = 3_000L;

	/** Subscriber emitter → the username it connected as (null when anonymous). */
	private final Map<SseEmitter, String> subscribers = new ConcurrentHashMap<>();

	/**
	 * Serializes broadcasts so concurrent ingests never write the same emitter at once.
	 */
	private final ReentrantLock broadcastLock = new ReentrantLock();

	private final MembershipService membership;

	/** Registers a new subscriber stream; it is removed automatically when it ends. */
	public SseEmitter subscribe(String username) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
		this.subscribers.put(emitter, (username != null) ? username : "");
		emitter.onCompletion(() -> this.subscribers.remove(emitter));
		emitter.onTimeout(() -> {
			this.subscribers.remove(emitter);
			emitter.complete();
		});
		emitter.onError((ex) -> this.subscribers.remove(emitter));
		try {
			// reconnectTime tells the browser EventSource how soon to retry after a drop.
			emitter.send(SseEmitter.event().name("connected").reconnectTime(RECONNECT_MS).data("ok"));
		}
		catch (IOException ex) {
			this.subscribers.remove(emitter);
		}
		return emitter;
	}

	/**
	 * Periodic keepalive: sends a comment to every subscriber so idle proxies don't drop
	 * the connection, and prunes any whose socket is already dead (the send fails).
	 * Without this a dead client lingers until its read timeout.
	 */
	@Scheduled(fixedRateString = "${unitrack.live.heartbeat-ms:25000}")
	public void heartbeat() {
		this.broadcastLock.lock();
		try {
			for (SseEmitter emitter : this.subscribers.keySet()) {
				try {
					emitter.send(SseEmitter.event().comment("ping"));
				}
				catch (IOException | RuntimeException ex) {
					this.subscribers.remove(emitter);
				}
			}
		}
		finally {
			this.broadcastLock.unlock();
		}
	}

	/**
	 * Broadcasts a run update to every subscriber that may read the project. Returns the
	 * number delivered (useful for tests/metrics).
	 */
	public int publish(Project project, RunUpdate update) {
		this.broadcastLock.lock();
		try {
			int delivered = 0;
			for (Map.Entry<SseEmitter, String> entry : this.subscribers.entrySet()) {
				String username = entry.getValue().isEmpty() ? null : entry.getValue();
				if (!this.membership.canRead(username, project)) {
					continue;
				}
				if (deliver(entry.getKey(), update)) {
					delivered++;
				}
			}
			return delivered;
		}
		finally {
			this.broadcastLock.unlock();
		}
	}

	public int subscriberCount() {
		return this.subscribers.size();
	}

	private boolean deliver(SseEmitter emitter, RunUpdate update) {
		try {
			emitter.send(SseEmitter.event().name("run").data(update));
			return true;
		}
		catch (IOException | RuntimeException ex) {
			log.debug("Dropping dead SSE subscriber", ex);
			this.subscribers.remove(emitter);
			return false;
		}
	}

}
