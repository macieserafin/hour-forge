package pl.macieserafin.hourforge.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordChangeRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword,
                                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword) {
    @Override
    public String toString() {
        return "PasswordChangeRequest[redacted]";
    }
}
