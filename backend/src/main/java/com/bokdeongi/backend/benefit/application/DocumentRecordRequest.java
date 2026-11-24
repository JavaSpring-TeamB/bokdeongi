package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 서류 발급일 기록 등록 요청 DTO
 */
public record DocumentRecordRequest(

        // 서류 종류 ID
        @NotNull(message = "서류 종류 ID가 필요합니다.")
        @Positive(message = "서류 종류 ID는 양수여야 합니다.")
        Long documentTypeId,

        // 서류 발급일
        @NotNull(message = "서류 발급일이 필요합니다.")
        @PastOrPresent(message = "서류 발급일은 미래일 수 없습니다.")
        LocalDate issueDate
) {
}