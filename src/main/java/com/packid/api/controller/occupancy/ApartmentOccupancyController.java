package com.packid.api.controller.occupancy;

import com.packid.api.controller.occupancy.dto.ApartmentOccupancyEndRequest;
import com.packid.api.controller.occupancy.dto.ApartmentOccupancyResponse;
import com.packid.api.controller.occupancy.dto.ApartmentOccupancyStartRequest;
import com.packid.api.service.ApartmentOccupancyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/occupancies")
public class ApartmentOccupancyController {

    private final ApartmentOccupancyService service;

    public ApartmentOccupancyController(ApartmentOccupancyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ApartmentOccupancyResponse>> list(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam String block,
            @RequestParam String apartment
    ) {
        return ResponseEntity.ok(service.list(user, block, apartment));
    }

    @PostMapping("/start")
    public ResponseEntity<ApartmentOccupancyResponse> start(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody ApartmentOccupancyStartRequest request
    ) {
        return ResponseEntity.ok(service.start(user, request));
    }

    @PostMapping("/end")
    public ResponseEntity<ApartmentOccupancyResponse> end(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody ApartmentOccupancyEndRequest request
    ) {
        return ResponseEntity.ok(service.end(user, request));
    }
}
