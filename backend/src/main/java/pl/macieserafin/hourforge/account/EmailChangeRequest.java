package pl.macieserafin.hourforge.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record EmailChangeRequest(@NotBlank @Email @Size(max = 255) String newEmail,
                                 @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {
    public EmailChangeRequest {
        newEmail = newEmail == null ? null : newEmail.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "EmailChangeRequest[redacted]";
    }
}
