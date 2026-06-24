package org.alexmond.unitrack.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How the branches picker decides which branches to surface, bound from
 * {@code unitrack.branches.*}. Ephemeral PR/feature branches (deleted after merge)
 * otherwise pile up forever because branches are derived from run history. Naming
 * conventions differ, so the long-lived set is configurable.
 */
@Component
@ConfigurationProperties(prefix = "unitrack.branches")
@Getter
@Setter
public class BranchProperties {

	/**
	 * Glob patterns ({@code *} = any run of characters) for long-lived branches that are
	 * always shown, regardless of activity (e.g. {@code main}, {@code release/*}). Adjust
	 * to your naming.
	 */
	private List<String> protectedPatterns = new ArrayList<>(
			List.of("main", "master", "develop", "trunk", "release/*", "hotfix/*"));

	/**
	 * Branches whose latest run is within this many days are shown; older ones collapse
	 * behind a "show all" toggle. 0 shows only protected/default branches.
	 */
	private int activeDays = 30;

}
