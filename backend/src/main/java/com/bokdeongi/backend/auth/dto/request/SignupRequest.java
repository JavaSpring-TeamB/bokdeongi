package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 일반 회원가입 요청 DTO
 * 회원 기본 정보와 약관 동의 목록을 전달한다.
 */
public record SignupRequest(

        // 이메일
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100)
        String email,

        // 비밀번호 (8~64자)
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password,

        // 회원 이름
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50)
        String name,

        // 연락처
        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 20)
        String phoneNumber,

        // 생년월일 (과거 날짜만 허용)
        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        // 약관 동의 목록
        @NotEmpty(message = "약관 동의 정보가 필요합니다.")
        List<@NotNull @Valid ConsentRequest> consents
) {
}