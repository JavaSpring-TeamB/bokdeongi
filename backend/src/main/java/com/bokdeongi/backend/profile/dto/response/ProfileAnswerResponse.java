package com.bokdeongi.backend.profile.dto.response;

/**
 * PROF-06: 저장된 추가 질문 답변 조회 응답.
 */
public record ProfileAnswerResponse(
        String key,
        String value
) {
}