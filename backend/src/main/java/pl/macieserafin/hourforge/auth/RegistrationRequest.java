package pl.macieserafin.hourforge.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.macieserafin.hourforge.user.EmailAddress;

public record RegistrationRequest(@NotBlank @Size(max = 255) String displayName,
                                  @NotBlank @Email @Size(max = 255) String email,
                                  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
                                  @Size(max = 4096) String botCheckToken) {
    public RegistrationRequest {
        displayName = displayName == null ? null : displayName.strip();
        email = EmailAddress.normalize(email);
    }

    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
