package pl.macieserafin.hourforge.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A one-time code for signing in without the authenticator app; only its hash is stored. */
@Entity
@Table(name = "two_factor_backup_codes")
public class BackupCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected BackupCode() {
    }

    public BackupCode(UUID userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public String getCodeHash() { return codeHash; }
    public Instant getUsedAt() { return usedAt; }
    public void use(Instant at) { this.usedAt = at; }
}
