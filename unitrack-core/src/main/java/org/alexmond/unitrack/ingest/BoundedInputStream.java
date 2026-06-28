package org.alexmond.unitrack.ingest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fails fast once more than {@code maxBytes} have been read, instead of letting a
 * pathologically large (or gzip-bombed) upload OOM the process. This is the
 * defense-in-depth size guard for #369: it counts the <em>decompressed</em> bytes a
 * parser actually consumes, so it bounds DOM/{@code readTree} parsers that buffer the
 * whole document as well as the streaming ones.
 *
 * <p>
 * A non-positive {@code maxBytes} disables the guard (unbounded).
 */
public final class BoundedInputStream extends FilterInputStream {

	private final long maxBytes;

	private final String label;

	private long count;

	/**
	 * @param in the stream to guard
	 * @param maxBytes the hard limit in bytes; {@code <= 0} disables the guard
	 * @param label a human name for the limit, used in the failure message (e.g.
	 * "report")
	 */
	public BoundedInputStream(InputStream in, long maxBytes, String label) {
		super(in);
		this.maxBytes = maxBytes;
		this.label = label;
	}

	@Override
	public int read() throws IOException {
		int b = super.read();
		if (b != -1) {
			tally(1);
		}
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = super.read(b, off, len);
		if (read > 0) {
			tally(read);
		}
		return read;
	}

	private void tally(long n) throws IOException {
		this.count += n;
		if (this.maxBytes > 0 && this.count > this.maxBytes) {
			// Signalled as IOException so it surfaces through every parser's stream-read
			// path (DOM/SAX, Jackson, line readers all already wrap stream IOExceptions
			// into an IngestException); the message is preserved in the failure reason.
			throw new IOException("Upload exceeds the " + this.label + " size limit of " + this.maxBytes
					+ " bytes (decompressed) — "
					+ "raise the limit or split the report. This guard prevents the parser from running out of memory.");
		}
	}

}
