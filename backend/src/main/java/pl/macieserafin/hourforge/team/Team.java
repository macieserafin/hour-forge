package pl.macieserafin.hourforge.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Team() {
    }

    public Team(String name, String timezone) {
        this.name = name;
        this.timezone = timezone;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
