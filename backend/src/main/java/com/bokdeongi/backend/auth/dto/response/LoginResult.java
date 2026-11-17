package com.bokdeongi.backend.auth.dto.response;

/**
 * 로그인 및 토큰 재발급 결과 DTO
 * Access Token 응답 정보와 Refresh Token을 함께 전달한다.
 *
 * Refresh Token은 응답 본문이 아닌
 * HttpOnly 쿠키에 설정하기 위해 별도로 보관한다.
 */
public record LoginResult(

        // 클라이언트 응답 본문에 포함할 로그인 정보
        LoginResponse loginResponse,

        // HttpOnly 쿠키에 저장할 Refresh Token
        String refreshToken
) {
}