package com.packid.api.controller.amenity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AmenityGuestRequest(@NotBlank @Size(max=160) String name, @Size(max=80) String document) {}
