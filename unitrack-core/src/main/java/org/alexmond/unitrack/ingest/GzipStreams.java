package org.alexmond.unitrack.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Transparently decompresses gzip-compressed uploads. Report files (JUnit/coverage XML)
 * compress ~10-20x, so the uploader can gzip each part to stay under request-size limits
 * (e.g. a proxy/CDN body cap); the server detects the gzip magic bytes and inflates.
 * Plain (uncompressed) streams pass through untouched, so old and new clients both work.
 */
public final class GzipStreams {

	private static final int GZIP_MAGIC_0 = 0x1f;

	private static final int GZIP_MAGIC_1 = 0x8b;

	private GzipStreams() {
	}

	/**
	 * Returns a stream that inflates {@code in} when it starts with the gzip magic bytes,
	 * or {@code in} (rewound) otherwise.
	 */
	public static InputStream gunzipIfNeeded(InputStream in) throws IOException {
		PushbackInputStream pushback = new PushbackInputStream(in, 2);
		byte[] signature = new byte[2];
		int read = pushback.read(signature, 0, 2);
		if (read > 0) {
			pushback.unread(signature, 0, read);
		}
		boolean gzipped = read == 2 && (signature[0] & 0xff) == GZIP_MAGIC_0 && (signature[1] & 0xff) == GZIP_MAGIC_1;
		return gzipped ? new GZIPInputStream(pushback) : pushback;
	}

}
