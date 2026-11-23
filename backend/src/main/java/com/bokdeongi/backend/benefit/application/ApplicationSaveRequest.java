package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ApplicationSaveRequest(
        @NotNull
        @Min(1)
        @Max(4)
        Integer step,

        Map<String, Object> appAnswers,

        Map<String, Object> docChecks
) {
}