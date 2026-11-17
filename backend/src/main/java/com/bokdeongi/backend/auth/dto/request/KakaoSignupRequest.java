package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 카카오 회원가입 요청 DTO
 * 카카오 인증 정보는 서버 세션에서 조회하고,
 * 클라이언트에서는 약관 동의 정보만 전달받는다.
 */
public record KakaoSignupRequest(

        // 약관 동의 목록 (최소 1개 이상)
        @NotEmpty(message = "약관 동의 정보가 필요합니다.")
        List<@Valid ConsentRequest> consents
) {
}