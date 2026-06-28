package org.alexmond.unitrack.web.ingest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Ingest limits, bound from {@code unitrack.ingest.*}. The size guards cap the
 * <em>decompressed</em> bytes a single uploaded part may consume, so a pathologically
 * large (or gzip-bombed) report fails fast with a clear message instead of OOM-ing the
 * process (#369).
 */
@Component
@ConfigurationProperties(prefix = "unitrack.ingest")
@Getter
@Setter
public class IngestProperties {

	/**
	 * Hard cap per test/coverage report part. These formats are still parsed via DOM /
	 * full-document read, so the limit is sized to the heap rather than to the file. A
	 * non-positive value disables the guard.
	 */
	private DataSize maxReportBytes = DataSize.ofMegabytes(256);

	/**
	 * Hard cap per performance-result part (JMeter JTL, Gatling, k6, JMH). The line-based
	 * perf parsers stream into a bounded histogram, so this is a generous backstop that
	 * still lets long soak runs through. A non-positive value disables the guard.
	 */
	private DataSize maxPerfBytes = DataSize.ofGigabytes(1);

	public long maxReportBytesValue() {
		return (this.maxReportBytes != null) ? this.maxReportBytes.toBytes() : -1;
	}

	public long maxPerfBytesValue() {
		return (this.maxPerfBytes != null) ? this.maxPerfBytes.toBytes() : -1;
	}

}
