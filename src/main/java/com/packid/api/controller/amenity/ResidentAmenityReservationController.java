package com.packid.api.controller.amenity;

import com.packid.api.controller.amenity.dto.*;
import com.packid.api.service.AmenityReservationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/resident/amenities")
public class ResidentAmenityReservationController {
    private final AmenityReservationService service;
    public ResidentAmenityReservationController(AmenityReservationService service) { this.service = service; }

    @GetMapping("/spaces")
    public List<AmenitySpaceResponse> spaces(HttpSession session) { return service.residentSpaces(session); }

    @GetMapping("/spaces/{id}/photo")
    public ResponseEntity<byte[]> photo(HttpSession session, @PathVariable UUID id) {
        var photo = service.photoResident(session, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(photo.bytes());
    }

    @GetMapping("/reservations")
    public List<AmenityReservationResponse> reservations(HttpSession session) { return service.residentReservations(session); }

    @GetMapping("/spaces/{id}/booked-dates")
    public List<LocalDate> bookedDates(
            HttpSession session,
            @PathVariable UUID id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return service.residentBookedDates(session, id, from, to);
    }

    @PostMapping("/reservations")
    public AmenityReservationResponse reserve(HttpSession session, @Valid @RequestBody AmenityReservationRequest request) {
        return service.reserve(session, request);
    }

    @PostMapping("/reservations/{id}/cancel")
    public AmenityReservationResponse cancel(HttpSession session, @PathVariable UUID id) { return service.cancelResident(session, id); }

    @PutMapping("/reservations/{id}/guests")
    public AmenityReservationResponse guests(HttpSession session, @PathVariable UUID id, @Valid @RequestBody List<@Valid AmenityGuestRequest> guests) {
        return service.replaceGuests(session, id, guests);
    }
}
