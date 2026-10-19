package pl.macieserafin.hourforge.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.net.URI;

public record ProjectRequest(@NotBlank @Size(max = 255) String name,
                             @Size(max = 2000) String description,
                             @Size(max = 2048) String repositoryUrl) {
    public ProjectRequest(String name, String description) { this(name, description, null); }

    public ProjectRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
        repositoryUrl = repositoryUrl == null || repositoryUrl.isBlank() ? null : repositoryUrl.strip();
    }

    @AssertTrue(message = "Repository URL must be an HTTPS GitHub repository without credentials, query or fragment")
    public boolean isRepositoryUrlValid() {
        if (repositoryUrl == null) return true;
        try {
            var uri = URI.create(repositoryUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null && uri.getPort() == -1
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getRawPath().matches("/[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+/?")
                    && !uri.getRawPath().matches(".*/\\.{1,2}/?");
        } catch (IllegalArgumentException ex) { return false; }
    }
}
