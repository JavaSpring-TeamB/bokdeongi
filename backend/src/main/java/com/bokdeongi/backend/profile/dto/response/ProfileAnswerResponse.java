package com.bokdeongi.backend.profile.dto.response;

import java.time.LocalDateTime;

/**
 * PROF-06: 저장된 추가 질문 답변 조회 응답.
 */
public record ProfileAnswerResponse(
        String key,
        String value,
        LocalDateTime answeredAt,
        boolean needsReconfirmation
) {
}