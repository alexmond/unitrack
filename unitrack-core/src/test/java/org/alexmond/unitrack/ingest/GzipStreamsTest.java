package org.alexmond.unitrack.ingest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GzipStreamsTest {

	@Test
	void inflatesGzippedStream() throws Exception {
		String xml = "<testsuite name=\"x\" tests=\"1\"/>";
		ByteArrayOutputStream gz = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
			out.write(xml.getBytes(StandardCharsets.UTF_8));
		}
		try (InputStream in = GzipStreams.gunzipIfNeeded(new ByteArrayInputStream(gz.toByteArray()))) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(xml);
		}
	}

	@Test
	void passesPlainStreamThrough() throws Exception {
		String xml = "<testsuite name=\"plain\"/>";
		try (InputStream in = GzipStreams
			.gunzipIfNeeded(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(xml);
		}
	}

	@Test
	void handlesEmptyStream() throws Exception {
		try (InputStream in = GzipStreams.gunzipIfNeeded(new ByteArrayInputStream(new byte[0]))) {
			assertThat(in.readAllBytes()).isEmpty();
		}
	}

}
