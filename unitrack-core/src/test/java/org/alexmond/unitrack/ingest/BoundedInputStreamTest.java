package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedInputStreamTest {

	private static InputStream of(int bytes) {
		return new ByteArrayInputStream(new byte[bytes]);
	}

	@Test
	void passesThroughWhenUnderTheLimit() throws Exception {
		try (InputStream in = new BoundedInputStream(of(100), 256, "report")) {
			assertThat(in.readAllBytes()).hasSize(100);
		}
	}

	@Test
	void failsFastOnceTheLimitIsExceeded() {
		assertThatThrownBy(() -> {
			try (InputStream in = new BoundedInputStream(of(1000), 256, "report")) {
				in.readAllBytes();
			}
		}).isInstanceOf(IOException.class).hasMessageContaining("size limit").hasMessageContaining("report");
	}

	@Test
	void exactlyAtTheLimitIsAllowed() throws Exception {
		try (InputStream in = new BoundedInputStream(of(256), 256, "report")) {
			assertThat(in.readAllBytes()).hasSize(256);
		}
	}

	@Test
	void nonPositiveLimitDisablesTheGuard() throws Exception {
		try (InputStream in = new BoundedInputStream(of(10_000), 0, "report")) {
			assertThat(in.readAllBytes()).hasSize(10_000);
		}
	}

	@Test
	void countsSingleByteReadsToo() {
		assertThatThrownBy(() -> {
			try (InputStream in = new BoundedInputStream(
					new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8)), 3, "perf")) {
				while (in.read() != -1) {
					// drain one byte at a time
				}
			}
		}).isInstanceOf(IOException.class).hasMessageContaining("perf");
	}

}
