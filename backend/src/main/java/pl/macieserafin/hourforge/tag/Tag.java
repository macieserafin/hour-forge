package pl.macieserafin.hourforge.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import pl.macieserafin.hourforge.project.ProjectColor;
import pl.macieserafin.hourforge.team.Team;

@Entity
@Table(name = "tags")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "color")
    private ProjectColor color;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
    }

    public Tag(Team team, String name, ProjectColor color) {
        this.team = team;
        this.name = name;
        this.color = color;
    }

    public UUID getId() { return id; }
    public Team getTeam() { return team; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProjectColor getColor() { return color; }
    public void setColor(ProjectColor color) { this.color = color; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
