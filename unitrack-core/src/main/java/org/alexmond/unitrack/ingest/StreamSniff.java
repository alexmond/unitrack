package org.alexmond.unitrack.ingest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a small head sample for format detection <em>without</em> buffering the whole
 * upload. The dispatchers mark/peek/reset a {@link BufferedInputStream} so the matched
 * parser then streams the full content itself — essential for multi-hour perf logs that
 * would OOM if read into a single {@code byte[]} or DOM.
 */
final class StreamSniff {

	private StreamSniff() {
	}

	/**
	 * Wraps {@code in} so a {@code headBytes} mark/reset is safe (buffer ≥ the sample).
	 */
	static BufferedInputStream buffered(InputStream in, int headBytes) {
		int buffer = Math.max(headBytes * 2, 8192);
		return (in instanceof BufferedInputStream b) ? b : new BufferedInputStream(in, buffer);
	}

	/** Peeks up to {@code headBytes} from the start, then rewinds the stream. */
	static String head(BufferedInputStream bis, int headBytes) throws IOException {
		bis.mark(headBytes + 1);
		byte[] buf = new byte[headBytes];
		int total = 0;
		int read;
		while (total < headBytes && (read = bis.read(buf, total, headBytes - total)) != -1) {
			total += read;
		}
		bis.reset();
		return new String(buf, 0, total, StandardCharsets.UTF_8);
	}

}
