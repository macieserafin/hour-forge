package pl.macieserafin.hourforge.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeleteAccountRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {
    @Override
    public String toString() {
        return "DeleteAccountRequest[redacted]";
    }
}
