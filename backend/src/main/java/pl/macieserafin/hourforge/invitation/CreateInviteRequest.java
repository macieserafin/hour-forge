package pl.macieserafin.hourforge.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** An {@code email} also sends the invitation to that address; the link works the same either way. */
public record CreateInviteRequest(Instant expiresAt, @Positive Integer maxUses, @Email @Size(max = 255) String email) {
    public CreateInviteRequest(Instant expiresAt, Integer maxUses) {
        this(expiresAt, maxUses, null);
    }

    public CreateInviteRequest {
        email = email == null || email.isBlank() ? null : email.strip();
    }
}
