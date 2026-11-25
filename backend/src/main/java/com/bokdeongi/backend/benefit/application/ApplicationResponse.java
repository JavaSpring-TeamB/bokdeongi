package com.bokdeongi.backend.benefit.application;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.LocalDateTime;

/**
 * 혜택 신청 진행 정보 응답 DTO.
 *
 * 신청 ID, 혜택 ID, 신청 상태, 진행 단계,
 * 저장된 답변 및 서류 체크 정보를 반환한다.
 *
 * 공고 변경 여부와 신청 진행률 정보도 제공한다.
 */
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