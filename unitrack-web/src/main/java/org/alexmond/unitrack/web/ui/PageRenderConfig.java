package org.alexmond.unitrack.web.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounded pool for rendering dashboard pages whose handlers fan out across many
 * independent read services (#280). Typed as {@link ExecutorService} so it injects
 * unambiguously (Boot's default task executor is an {@code AsyncTaskExecutor}).
 * {@code CallerRunsPolicy} degrades to sequential rather than rejecting when saturated.
 */
@Configuration
class PageRenderConfig {

	@Bean(destroyMethod = "shutdown")
	ExecutorService pageRenderExecutor() {
		AtomicInteger n = new AtomicInteger();
		return new ThreadPoolExecutor(6, 12, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), (r) -> {
			Thread t = new Thread(r, "page-render-" + n.incrementAndGet());
			t.setDaemon(true);
			return t;
		}, new ThreadPoolExecutor.CallerRunsPolicy());
	}

}
