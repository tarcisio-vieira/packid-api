package com.packid.api.controller.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnouncementRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 5_500_000) String bodyHtml
) {}
