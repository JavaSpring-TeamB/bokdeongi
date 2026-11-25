package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 기존 카카오 회원의 필수 약관 재동의 요청 DTO.
 */
public record KakaoReconsentRequest(

        @NotEmpty(message = "약관 동의 정보가 필요합니다.")
        List<@Valid ConsentRequest> consents
) {
}