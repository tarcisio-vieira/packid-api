package com.packid.api.controller.pool.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PoolCardRequest(
        @NotNull UUID residentRegistryEntryId,
        @NotNull LocalDate issueDate,
        Boolean underTen
) {}
