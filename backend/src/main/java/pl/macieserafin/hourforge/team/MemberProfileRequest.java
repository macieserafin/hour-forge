package pl.macieserafin.hourforge.team;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Both values are optional; an empty title or a missing size of the job clears it. */
public record MemberProfileRequest(@Size(max = 80) String jobTitle, @Min(1) @Max(100) Integer ftePercent) {
    public MemberProfileRequest {
        jobTitle = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.strip();
    }
}
