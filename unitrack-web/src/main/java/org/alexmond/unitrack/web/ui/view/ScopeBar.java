package org.alexmond.unitrack.web.ui.view;

import java.util.List;

/**
 * The scope control for an analytics tab — dropdowns that re-enter the same tab scoped to
 * a different upload flag/series and/or git branch (the resolved "flag/branch =
 * dropdowns, module = breakdown list" idiom; replaced the old Load pill bar). Each
 * dropdown renders only when there's a real choice, and only tabs whose queries support a
 * dimension pass it (branch is Tests/Timing today; Load/Coverage branch-scoping is
 * tracked in #431/#430).
 *
 * @param basePath the tab path to navigate to (e.g. {@code /projects/1/tests})
 * @param flags the available flags/series (null/≤1 → no flag dropdown)
 * @param selectedFlag the currently-shown flag
 * @param branches the available branches (null/≤1 → no branch dropdown)
 * @param selectedBranch the currently-shown branch, or null for "all branches"
 * @param module the current module scope to preserve across a switch, or null
 */
public record ScopeBar(String basePath, List<String> flags, String selectedFlag, List<String> branches,
		String selectedBranch, String module) {

	/** Whether to render the bar at all (needs at least one real choice). */
	public boolean hasChoice() {
		return hasFlagChoice() || hasBranchChoice();
	}

	/** Whether the flag dropdown is worth showing (a single flag is no choice). */
	public boolean hasFlagChoice() {
		return this.flags != null && this.flags.size() > 1;
	}

	/**
	 * Whether the branch dropdown is worth showing (>1 branch to choose from + "all").
	 */
	public boolean hasBranchChoice() {
		return this.branches != null && this.branches.size() > 1;
	}

}
