package com.bokdeongi.backend.auth.dto.response;

/**
 * 회원가입 응답 DTO
 * 일반 회원가입과 카카오 회원가입이 완료되면
 * 생성된 회원 정보와 결과 메시지를 반환한다.
 */
public record SignupResponse(

        // 생성된 회원의 고유 ID
        Long userId,

        // 가입한 회원의 이메일
        String email,

        // 회원가입 결과 메시지
        String message
) {
}