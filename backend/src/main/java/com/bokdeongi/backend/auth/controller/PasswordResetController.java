package com.bokdeongi.backend.auth.controller;

import com.bokdeongi.backend.auth.dto.request.PasswordResetCodeRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetVerifyRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetRequest;
import com.bokdeongi.backend.auth.dto.response.PasswordResetCodeResponse;
import com.bokdeongi.backend.auth.dto.response.PasswordResetVerifyResponse;
import com.bokdeongi.backend.auth.service.PasswordResetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 비밀번호 찾기 및 재설정 API 컨트롤러.
 *
 * 로그인하지 않은 회원의 이메일 인증번호 발급,
 * 인증번호 검증 및 새로운 비밀번호 설정을 처리한다.
 */
@Tag(name = "비밀번호 찾기")
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetService passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * 가입 이메일로 비밀번호 재설정 인증번호를 발송한다.
     */
    @Operation(
            summary = "비밀번호 재설정 인증번호 발송",
            description = "가입 이메일로 인증번호를 발송합니다."
    )
    @PostMapping("/send-code")
    public ResponseEntity<PasswordResetCodeResponse> sendCode(
            @Valid @RequestBody PasswordResetCodeRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(passwordResetService.sendCode(request));
    }

    /**
     * 이메일 인증번호를 검증하고 일회용 재설정 토큰을 발급한다.
     */
    @Operation(
            summary = "비밀번호 재설정 인증번호 검증",
            description = "인증 성공 시 일회용 재설정 토큰을 발급합니다."
    )
    @PostMapping("/verify-code")
    public ResponseEntity<PasswordResetVerifyResponse> verifyCode(
            @Valid @RequestBody PasswordResetVerifyRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(passwordResetService.verifyCode(request));
    }

    /**
     * 일회용 재설정 토큰을 이용해 새로운 비밀번호를 설정한다.
     */
    @Operation(
            summary = "비밀번호 재설정",
            description = "새 비밀번호를 저장하고 기존 로그인 토큰을 무효화합니다."
    )
    @PostMapping("/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.resetPassword(request);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}