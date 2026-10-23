package pl.macieserafin.hourforge.image;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
public class PictureController {
    private final PictureService pictures;

    public PictureController(PictureService pictures) {
        this.pictures = pictures;
    }

    @PutMapping("/api/account/avatar")
    public ResponseEntity<Void> setAvatar(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam("image") MultipartFile image) throws IOException {
        pictures.setAvatar(principal.getUserId(), image.getBytes());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/account/avatar")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal AppUserPrincipal principal) {
        pictures.removeAvatar(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return picture(pictures.avatar(principal.getUserId(), id));
    }

    @PutMapping("/api/teams/{id}/logo")
    public ResponseEntity<Void> setLogo(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id,
                                        @RequestParam("image") MultipartFile image) throws IOException {
        pictures.setTeamLogo(principal.getUserId(), id, image.getBytes());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/teams/{id}/logo")
    public ResponseEntity<Void> removeLogo(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        pictures.removeTeamLogo(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/teams/{id}/logo")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return picture(pictures.teamLogo(principal.getUserId(), id));
    }

    /**
     * Always our own JPEG, never the upload: a fixed type, no sniffing, nothing that could run, and private caching
     * (the page asks with ?v=updatedAt, so a change is fetched at once).
     */
    private static ResponseEntity<byte[]> picture(byte[] jpeg) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(jpeg);
    }
}
