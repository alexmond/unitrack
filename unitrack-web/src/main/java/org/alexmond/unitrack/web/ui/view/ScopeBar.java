package org.alexmond.unitrack.web.ui.view;

import java.util.List;

/**
 * The flag scope control for an analytics tab — a dropdown that re-enters the same tab
 * scoped to a different upload flag/series (replacing the old Load pill bar; the resolved
 * "flag = dropdown, module = breakdown list" idiom). Rendered only when there's a real
 * choice ({@code >1} flag).
 *
 * @param basePath the tab path to navigate to (e.g. {@code /projects/1/tests})
 * @param flags the available flags/series
 * @param selectedFlag the currently-shown flag
 * @param module the current module scope to preserve across a flag switch, or null
 */
public record ScopeBar(String basePath, List<String> flags, String selectedFlag, String module) {

	/** Whether to render the control at all (a single flag is no choice). */
	public boolean hasChoice() {
		return this.flags != null && this.flags.size() > 1;
	}

}
