package pl.macieserafin.hourforge.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One sign-in attempt on an existing account, with the device as the person would recognise it. */
@Entity
@Table(name = "login_events")
public class LoginEvent {
    public enum Outcome { SUCCESS, FAILED }

    /** The first step of the sign-in; rows from before V37 are PASSWORD, the only method there was. */
    public enum Method { PASSWORD, GOOGLE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false)
    private Outcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    private Method method;

    @Column(name = "browser", updatable = false)
    private String browser;

    @Column(name = "system", updatable = false)
    private String system;

    @Column(name = "address", updatable = false)
    private String address;

    @Column(name = "location", updatable = false)
    private String location;

    protected LoginEvent() {
    }

    public LoginEvent(UUID userId, Instant occurredAt, Outcome outcome, Method method, DeviceInfo device) {
        this.userId = userId;
        this.occurredAt = occurredAt;
        this.outcome = outcome;
        this.method = method;
        this.browser = device.browser();
        this.system = device.system();
        this.address = device.address();
        this.location = device.location();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getOccurredAt() { return occurredAt; }
    public Outcome getOutcome() { return outcome; }
    public Method getMethod() { return method; }
    public String getBrowser() { return browser; }
    public String getSystem() { return system; }
    public String getAddress() { return address; }
    public String getLocation() { return location; }
}
