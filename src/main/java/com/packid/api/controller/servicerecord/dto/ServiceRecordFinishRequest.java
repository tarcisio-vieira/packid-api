package com.packid.api.controller.servicerecord.dto;

import java.time.LocalDateTime;

public record ServiceRecordFinishRequest(
        LocalDateTime completedAt
) {}
