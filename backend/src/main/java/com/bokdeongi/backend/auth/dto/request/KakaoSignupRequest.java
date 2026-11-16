package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record KakaoSignupRequest(

        @NotEmpty(message = "약관 동의 정보가 필요합니다.")
        List<@Valid ConsentRequest> consents

) {
}