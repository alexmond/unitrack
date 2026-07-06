package org.alexmond.unitrack.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.alexmond.unitrack.config.BranchProperties;
import org.alexmond.unitrack.domain.Project;
import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.repository.ProjectRepository;
import org.alexmond.unitrack.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BranchCleanupServiceTest {

	@Mock
	private ProjectRepository projects;

	@Mock
	private TestRunRepository runs;

	@Mock
	private ProjectSettingsService settings;

	@Mock
	private BranchProperties branchProps;

	@Mock
	private RunDeletionService runDeletion;

	@InjectMocks
	private BranchCleanupService cleanup;

	@Test
	void expiresStaleNonProtectedBranchesOnly() {
		Instant now = Instant.parse("2026-06-29T00:00:00Z");
		given(branchProps.getRetainDays()).willReturn(30);
		given(branchProps.getProtectedPatterns()).willReturn(List.of("main", "release/*"));
		Project project = mock(Project.class);
		given(project.getId()).willReturn(1L);
		given(projects.findAll()).willReturn(List.of(project));
		given(runs.findLatestRunId(1L)).willReturn(99L);
		given(settings.gateConfig(1L)).willReturn(new GateConfig("main", null, 0.0, false));
		given(runs.findDistinctBranches(1L)).willReturn(List.of("main", "release/1.0", "feature/old", "feature/fresh"));

		TestRun old = mock(TestRun.class);
		given(old.getCreatedAt()).willReturn(now.minus(60, ChronoUnit.DAYS));
		given(runs.findLatestByBranch(eq(1L), eq("feature/old"), isNull(), any())).willReturn(List.of(old));
		TestRun fresh = mock(TestRun.class);
		given(fresh.getCreatedAt()).willReturn(now.minus(2, ChronoUnit.DAYS));
		given(runs.findLatestByBranch(eq(1L), eq("feature/fresh"), isNull(), any())).willReturn(List.of(fresh));
		given(runs.findIdsByProjectIdAndBranch(1L, "feature/old")).willReturn(List.of(10L, 11L));

		int deleted = cleanup.expireStaleBranches(now);

		assertThat(deleted).isEqualTo(2);
		verify(runDeletion).deleteRun(10L);
		verify(runDeletion).deleteRun(11L);
		verify(runDeletion, never()).deleteRun(99L);
		// Protected, default and still-fresh branches are never purged.
		verify(runs, never()).findIdsByProjectIdAndBranch(1L, "main");
		verify(runs, never()).findIdsByProjectIdAndBranch(1L, "feature/fresh");
	}

	@Test
	void retainDaysZeroDisablesExpiry() {
		given(branchProps.getRetainDays()).willReturn(0);
		assertThat(cleanup.expireStaleBranches(Instant.now())).isZero();
		verifyNoInteractions(projects, runDeletion);
	}

	@Test
	void deleteBranchSkipsTheDefaultBranch() {
		given(branchProps.getProtectedPatterns()).willReturn(List.of("main"));
		given(settings.gateConfig(1L)).willReturn(new GateConfig("develop", null, 0.0, false));
		assertThat(cleanup.deleteBranch(1L, "develop")).isEqualTo(-1);
		verifyNoInteractions(runDeletion);
	}

	@Test
	void deleteBranchRemovesEveryRunOfANormalBranch() {
		given(branchProps.getProtectedPatterns()).willReturn(List.of("main"));
		given(settings.gateConfig(1L)).willReturn(new GateConfig("main", null, 0.0, false));
		given(runs.findIdsByProjectIdAndBranch(1L, "feature/x")).willReturn(List.of(7L, 8L));
		assertThat(cleanup.deleteBranch(1L, "feature/x")).isEqualTo(2);
		verify(runDeletion).deleteRun(7L);
		verify(runDeletion).deleteRun(8L);
	}

}
