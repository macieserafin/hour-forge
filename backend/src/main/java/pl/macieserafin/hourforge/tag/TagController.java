package pl.macieserafin.hourforge.tag;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/teams/{teamId}/tags")
public class TagController {
    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<TagResponse> list(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                                  @RequestParam(defaultValue = "false") boolean includeArchived) {
        return tags.list(principal.getUserId(), teamId, includeArchived);
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                                              @Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tags.create(principal.getUserId(), teamId, request));
    }

    @PutMapping("/{tagId}")
    public TagResponse update(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                              @PathVariable UUID tagId, @Valid @RequestBody TagRequest request) {
        return tags.update(principal.getUserId(), teamId, tagId, request);
    }

    @PostMapping("/{tagId}/archive")
    public TagResponse archive(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId, @PathVariable UUID tagId) {
        return tags.archive(principal.getUserId(), teamId, tagId);
    }

    @PostMapping("/{tagId}/restore")
    public TagResponse restore(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId, @PathVariable UUID tagId) {
        return tags.restore(principal.getUserId(), teamId, tagId);
    }
}
