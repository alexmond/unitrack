package org.alexmond.unitrack.web.ingest;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded worker pool for async ingest (#368). Core == max with a bounded queue and
 * an abort policy, so once the pool and queue are saturated a new async upload is
 * rejected ({@link java.util.concurrent.RejectedExecutionException} → HTTP 429) rather
 * than piling up unbounded. Waits for in-flight jobs to finish on shutdown so they aren't
 * orphaned.
 */
@Configuration(proxyBeanMethods = false)
class IngestExecutorConfig {

	@Bean
	ThreadPoolTaskExecutor ingestExecutor(IngestProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("ingest-");
		executor.setCorePoolSize(properties.getAsyncPoolSize());
		executor.setMaxPoolSize(properties.getAsyncPoolSize());
		executor.setQueueCapacity(properties.getAsyncQueueCapacity());
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}

}
