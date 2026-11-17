package com.bokdeongi.backend.auth.dto.response;

/**
 * 로그인 및 토큰 재발급 응답 DTO
 * 클라이언트가 API 인증에 사용할
 * Access Token 정보와 유효기간을 반환한다.
 */
public record LoginResponse(

        // API 요청에 사용할 Access Token (JWT)
        String accessToken,

        // 인증 방식 (Bearer)
        String tokenType,

        // Access Token 유효기간 (초 단위)
        long expiresIn
) {
}