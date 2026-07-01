package org.alexmond.unitrack.web.ui;

import java.util.List;

import org.alexmond.unitrack.web.ui.view.KpiTile;

/**
 * The KPI tiles plus the raw counts the Tests roster's counts strip needs, computed
 * together from the same (optionally module-scoped) aggregation so the tiles and the
 * strip never disagree. {@code passed} is derived as {@code tests - failures - skipped}.
 */
record TileData(List<KpiTile> kpis, long tests, long failures, long skipped) {

	long passed() {
		return tests - failures - skipped;
	}

}
