package io.github.alexmond.unitrack.repository;

import io.github.alexmond.unitrack.domain.CoverageFileEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoverageFileEntryRepository extends JpaRepository<CoverageFileEntry, Long> {

    List<CoverageFileEntry> findByReportIdOrderByLineMissedDescPackageNameAsc(Long reportId);
}
