package io.github.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A unit of work being tracked — typically one source repository. */
@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Project() {
    }

    public Project(String name, String repoUrl) {
        this.name = name;
        this.repoUrl = repoUrl;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
