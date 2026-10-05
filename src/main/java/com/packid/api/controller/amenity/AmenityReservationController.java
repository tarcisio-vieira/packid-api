package com.packid.api.controller.amenity;

import com.packid.api.controller.amenity.dto.AmenityReservationResponse;
import com.packid.api.domain.model.AmenityReservation;
import com.packid.api.service.AmenityReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/amenity-reservations")
public class AmenityReservationController {
    private final AmenityReservationService service;
    public AmenityReservationController(AmenityReservationService service) { this.service = service; }

    @GetMapping
    public List<AmenityReservationResponse> list(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(required=false) UUID spaceId,
            @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) AmenityReservation.Status status,
            @RequestParam(defaultValue="false") boolean includePast) {
        return service.staffReservations(user, spaceId, from, to, status, includePast);
    }
}
