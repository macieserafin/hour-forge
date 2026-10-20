package pl.macieserafin.hourforge.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import pl.macieserafin.hourforge.team.Team;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "repository_url", length = 2048)
    private String repositoryUrl;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "color")
    private ProjectColor color;

    public ProjectColor getColor() { return color; }
    public void setColor(ProjectColor color) { this.color = color; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(Team team, String name, String description) {
        this.team = team;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
