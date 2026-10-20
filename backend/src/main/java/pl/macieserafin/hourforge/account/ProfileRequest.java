package pl.macieserafin.hourforge.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(@NotBlank @Size(max = 255) String displayName) {
    public ProfileRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}
