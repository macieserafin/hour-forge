package pl.macieserafin.hourforge.operator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The notice shown to everybody on this instance; a single row (id 1). */
@Entity
@Table(name = "instance_announcement")
public class Announcement {
    public enum Level { INFO, WARNING }

    static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "message", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private Level level;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Announcement() {
    }

    Announcement(String message, Level level, Instant updatedAt) {
        this.id = ID;
        update(message, level, updatedAt);
    }

    void update(String message, Level level, Instant updatedAt) {
        this.message = message;
        this.level = level;
        this.updatedAt = updatedAt;
    }

    public String getMessage() { return message; }
    public Level getLevel() { return level; }
    public Instant getUpdatedAt() { return updatedAt; }
}
