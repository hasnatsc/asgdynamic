package com.asg.fabricerp.profile;

import com.asg.fabricerp.security.CurrentUser;
import com.asg.fabricerp.security.FabricUserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * My profile: the page, its JSON, and the photo. Every write is to the caller's own account - no
 * route here takes the user to change from the request.
 *
 * <p>A photo is served with a one-year immutable cache: its URL carries {@code photo_version}, which
 * changes with every new photo, so a stale one is never shown and an unchanged one is never
 * fetched twice. A request for an old version still gets the current photo, just not cached.
 */
@Controller
public class ProfileController {

    public static final String PATH = "/account/profile";

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping(PATH)
    @PreAuthorize("isAuthenticated()")
    public String page(Model model) {
        model.addAttribute("title", "My profile");
        model.addAttribute("profile", profiles.profile(CurrentUser.id()));
        model.addAttribute("maxUploadMb", ProfilePhotoProcessor.MAX_UPLOAD_BYTES / (1024 * 1024));
        model.addAttribute("content", "account/profile :: content");
        return "layout/main";
    }

    @GetMapping("/api" + PATH)
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public Map<String, Object> read() {
        return profiles.profile(CurrentUser.id());
    }

    @PostMapping("/api" + PATH)
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public Map<String, Object> update(@RequestBody ProfileService.ProfileRequest request) {
        profiles.update(CurrentUser.id(), request);
        return profiles.profile(CurrentUser.id());
    }

    @PostMapping(value = "/api" + PATH + "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public Map<String, Object> upload(@RequestParam("photo") MultipartFile photo) throws IOException {
        if (photo.isEmpty()) throw new IllegalArgumentException("Choose a picture to upload");
        if (photo.getSize() > ProfilePhotoProcessor.MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The picture is larger than 5 MB");
        }
        profiles.setPhoto(CurrentUser.id(), photo.getBytes());
        return profiles.profile(CurrentUser.id());
    }

    @DeleteMapping("/api" + PATH + "/photo")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public Map<String, Object> removePhoto() {
        profiles.removePhoto(CurrentUser.id());
        return profiles.profile(CurrentUser.id());
    }

    /** A colleague's photo: any signed-in user of the same organizations may see it. */
    @GetMapping("/account/photo/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> photo(@PathVariable Long userId,
                                        @RequestParam(defaultValue = "full") String size,
                                        @RequestParam(name = "v", required = false) Long version,
                                        WebRequest request) {
        Set<Long> orgs = CurrentUser.principal().map(FabricUserPrincipal::getOrganizationIds).orElse(Set.of());
        return profiles.photo(userId, "thumb".equals(size), orgs)
            .map(p -> {
                String etag = "\"" + p.version() + ("thumb".equals(size) ? "-t" : "-f") + "\"";
                if (request.checkNotModified(etag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).<byte[]>build();
                }
                CacheControl cache = version != null && version == p.version()
                    ? CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable()
                    : CacheControl.noCache().cachePrivate();
                return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(cache).eTag(etag)
                    .header("X-Content-Type-Options", "nosniff").body(p.bytes());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
