package com.packid.api.controller.space;

import com.packid.api.controller.space.dto.SpaceAccessResponse;
import com.packid.api.domain.model.SpaceAccessRequest;
import com.packid.api.service.SpaceAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/space-access")
public class SpaceAccessController {
    private final SpaceAccessService service;

    public SpaceAccessController(SpaceAccessService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<SpaceAccessResponse> pending(@AuthenticationPrincipal OidcUser user) {
        return service.pending(user);
    }

    @GetMapping
    public List<SpaceAccessResponse> report(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(required = false) SpaceAccessRequest.SpaceType spaceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.report(user, spaceType, from, to);
    }

    @PostMapping("/{id}/release")
    public SpaceAccessResponse release(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return service.release(user, id);
    }

    @PostMapping("/{id}/complete")
    public SpaceAccessResponse complete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return service.complete(user, id);
    }
}
