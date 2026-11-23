package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(
        @NotNull Status status
) {
    public enum Status {
        SUBMITTED,
        SELECTED,
        REJECTED,
        CANCELED
    }
}