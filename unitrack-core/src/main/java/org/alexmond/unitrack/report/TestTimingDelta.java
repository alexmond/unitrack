package org.alexmond.unitrack.report;

/**
 * How one test's duration changed between two runs — the per-test "why did the suite time
 * move" breakdown (#327). {@code baseMs}/{@code headMs} are null for a test that was
 * added or removed between the runs.
 *
 * @param test the test identifier ({@code Class#method})
 * @param baseMs duration in the base run, or null if the test is new
 * @param headMs duration in the head run, or null if the test was removed/skipped
 * @param deltaMs head − base (for added: +head; for removed: −base) — sign = slower(+)
 * @param kind the change category
 */
public record TestTimingDelta(String test, Long baseMs, Long headMs, long deltaMs, Kind kind) {

	public enum Kind {

		SLOWER, FASTER, ADDED, REMOVED

	}

}
