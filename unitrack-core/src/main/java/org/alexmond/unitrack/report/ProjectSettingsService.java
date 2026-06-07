package org.alexmond.unitrack.report;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.unitrack.config.QualityGateProperties;
import org.alexmond.unitrack.domain.ProjectSettings;
import org.alexmond.unitrack.repository.ProjectSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the effective {@link GateConfig} for a project (per-project overrides over the
 * global {@link QualityGateProperties} defaults) and persists the overrides for the
 * settings UI.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectSettingsService {

	private final ProjectSettingsRepository repo;

	private final QualityGateProperties globals;

	/**
	 * Effective gate config for a project: overrides where set, global defaults
	 * otherwise.
	 */
	public GateConfig gateConfig(Long projectId) {
		ProjectSettings s = this.repo.findByProjectId(projectId).orElse(null);
		if (s == null) {
			return new GateConfig(this.globals.getBaseBranch(), this.globals.getMinLineCoverage(),
					this.globals.getMaxCoverageDropPct(), this.globals.isFailOnNewFailures());
		}
		return new GateConfig((s.getBaseBranch() != null) ? s.getBaseBranch() : this.globals.getBaseBranch(),
				(s.getMinLineCoverage() != null) ? s.getMinLineCoverage() : this.globals.getMinLineCoverage(),
				(s.getMaxCoverageDropPct() != null) ? s.getMaxCoverageDropPct() : this.globals.getMaxCoverageDropPct(),
				(s.getFailOnNewFailures() != null) ? s.getFailOnNewFailures() : this.globals.isFailOnNewFailures());
	}

	/** The stored overrides for a project (for the settings form), if any. */
	public Optional<ProjectSettings> find(Long projectId) {
		return this.repo.findByProjectId(projectId);
	}

	/**
	 * The global defaults, shown as placeholders/inherited values in the settings form.
	 */
	public GateConfig globals() {
		return new GateConfig(this.globals.getBaseBranch(), this.globals.getMinLineCoverage(),
				this.globals.getMaxCoverageDropPct(), this.globals.isFailOnNewFailures());
	}

	/**
	 * Saves per-project overrides; null values clear an override back to the global
	 * default.
	 */
	@Transactional
	public void save(Long projectId, String baseBranch, Double minLineCoverage, Double maxCoverageDropPct,
			Boolean failOnNewFailures) {
		ProjectSettings s = this.repo.findByProjectId(projectId).orElseGet(() -> new ProjectSettings(projectId));
		s.setBaseBranch((baseBranch != null && !baseBranch.isBlank()) ? baseBranch.trim() : null);
		s.setMinLineCoverage(minLineCoverage);
		s.setMaxCoverageDropPct(maxCoverageDropPct);
		s.setFailOnNewFailures(failOnNewFailures);
		this.repo.save(s);
	}

}
