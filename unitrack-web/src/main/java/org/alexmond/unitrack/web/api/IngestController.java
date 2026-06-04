package org.alexmond.unitrack.web.api;

import org.alexmond.unitrack.domain.TestRun;
import org.alexmond.unitrack.ingest.IngestException;
import org.alexmond.unitrack.ingest.IngestRequest;
import org.alexmond.unitrack.ingest.IngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Accepts CI uploads of test + coverage reports.
 *
 * <pre>
 * curl -F project=myapp -F branch=main -F commit=$SHA \
 *      -F 'junit=@target/surefire-reports/TEST-*.xml' \
 *      -F 'jacoco=@target/site/jacoco/jacoco.xml' \
 *      http://localhost:8080/api/v1/ingest
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(path = "/ingest", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponses.IngestResultJson> ingest(
            @RequestParam String project,
            @RequestParam(required = false) String repoUrl,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String commit,
            @RequestParam(required = false) String buildUrl,
            @RequestParam(required = false) String ciProvider,
            @RequestParam(name = "junit") List<MultipartFile> junit,
            @RequestParam(name = "jacoco", required = false) List<MultipartFile> jacoco) {

        IngestRequest meta = new IngestRequest(project, repoUrl, branch, commit, buildUrl, ciProvider);
        TestRun run = ingestService.ingest(meta, toSuppliers(junit), toSuppliers(jacoco));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.IngestResultJson.of(run));
    }

    private static List<Supplier<InputStream>> toSuppliers(List<MultipartFile> files) {
        List<Supplier<InputStream>> suppliers = new ArrayList<>();
        if (files == null) {
            return suppliers;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            suppliers.add(() -> {
                try {
                    return file.getInputStream();
                } catch (IOException e) {
                    throw new IngestException("Could not read upload '" + file.getOriginalFilename() + "'", e);
                }
            });
        }
        return suppliers;
    }
}
