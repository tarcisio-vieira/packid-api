package com.packid.api.service;

import java.util.UUID;

public record PackIdCreatedEvent(UUID tenantId, UUID packIdId) {
}