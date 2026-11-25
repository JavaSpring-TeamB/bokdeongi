package com.bokdeongi.backend.benefit.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 신청 진행 상태 자동 저장 요청 DTO.
 *
 * 현재 진행 단계와 신청 답변, 서류 체크 정보를 전달한다.
 * 답변 및 서류 체크 정보는 기존 저장 데이터와 병합된다.
 */
public record ApplicationSaveRequest(
        @NotNull
        @Min(1)
        @Max(4)
        Integer step,

        Map<String, Object> appAnswers,

        Map<String, Object> docChecks
) {
}