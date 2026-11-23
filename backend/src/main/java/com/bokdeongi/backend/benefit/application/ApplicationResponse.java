package com.bokdeongi.backend.benefit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

public record ApplicationResponse(
        long id,
        long benefitId,
        String appStatus,
        Integer step,
        @JsonRawValue String appAnswers,
        @JsonRawValue String docChecks,
        LocalDateTime startedApiModifiedAt,
        boolean announcementChanged,
        String stage,
        int completedItems,
        int totalItems,
        int progressPercent
) {
}