package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ApplicationStartRequest(
        @NotNull(message = "혜택 ID는 필수입니다.")
        @Positive(message = "혜택 ID는 1 이상이어야 합니다.")
        Long benefitId
) {
}