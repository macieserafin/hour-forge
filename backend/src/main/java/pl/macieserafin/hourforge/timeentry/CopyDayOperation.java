package pl.macieserafin.hourforge.timeentry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The outcome of one "repeat day" request, kept briefly so that a repeated request does not copy the day twice. */
@Entity
@Table(name = "copy_day_requests")
@IdClass(CopyDayOperation.Key.class)
public class CopyDayOperation {
    public record Key(UUID userId, UUID requestId) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "from_date", nullable = false, updatable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false, updatable = false)
    private LocalDate toDate;

    @Column(name = "copied", nullable = false, updatable = false)
    private int copied;

    @Column(name = "total_seconds", nullable = false, updatable = false)
    private long totalSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CopyDayOperation() {
    }

    public CopyDayOperation(UUID userId, CopyDayRequest request, CopyDayResponse result, Instant createdAt) {
        this.userId = userId;
        this.requestId = request.requestId();
        this.teamId = request.teamId();
        this.fromDate = request.fromDate();
        this.toDate = request.toDate();
        this.copied = result.copied();
        this.totalSeconds = result.totalSeconds();
        this.createdAt = createdAt;
    }

    public boolean answers(CopyDayRequest request) {
        return teamId.equals(request.teamId()) && fromDate.equals(request.fromDate()) && toDate.equals(request.toDate());
    }

    public CopyDayResponse result() {
        return new CopyDayResponse(copied, totalSeconds);
    }
}
