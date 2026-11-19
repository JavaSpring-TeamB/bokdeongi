package com.bokdeongi.backend.auth.dto.response;

/**
 * 회원가입 이메일 사용 가능 여부 응답 DTO.
 *
 * @param available true: 사용 가능, false: 이미 가입된 이메일
 */
public record EmailAvailabilityResponse(
        boolean available
) {
}