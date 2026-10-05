package com.packid.api.controller.announcement;

import com.packid.api.controller.announcement.dto.AnnouncementRequest;
import com.packid.api.controller.announcement.dto.AnnouncementResponse;
import com.packid.api.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService service;
    public AnnouncementController(AnnouncementService service) { this.service = service; }

    @GetMapping
    public List<AnnouncementResponse> list(@AuthenticationPrincipal OidcUser user) {
        return service.staffList(user);
    }

    @PostMapping
    public AnnouncementResponse publish(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody AnnouncementRequest request) {
        return service.publish(user, request);
    }
}
