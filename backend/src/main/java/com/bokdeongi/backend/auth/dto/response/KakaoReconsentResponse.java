package com.bokdeongi.backend.auth.dto.response;

/**
 * 기존 카카오 회원의 필수 약관 재동의 응답 DTO.
 */
public record KakaoReconsentResponse(

        Long userId,

        boolean profileOnboarding,

        String message
) {
}