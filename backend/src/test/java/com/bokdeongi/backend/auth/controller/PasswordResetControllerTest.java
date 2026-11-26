package com.bokdeongi.backend.auth.controller;

import com.bokdeongi.backend.auth.dto.request.PasswordResetCodeRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetVerifyRequest;
import com.bokdeongi.backend.auth.dto.request.PasswordResetRequest;
import com.bokdeongi.backend.auth.dto.response.PasswordResetCodeResponse;
import com.bokdeongi.backend.auth.dto.response.PasswordResetVerifyResponse;
import com.bokdeongi.backend.auth.service.PasswordResetService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 비밀번호 재설정 API 컨트롤러 테스트.
 *
 * 요청 데이터 검증, 정상 응답 구조 및
 * 비밀번호 재설정 예외 처리를 검증한다.
 */
class PasswordResetControllerTest {

    private static final String BASE_URL =
            "/api/auth/password-reset";

    private static final String EMAIL =
            "test@example.com";

    private static final String RESET_TOKEN =
            "test-reset-token";

    private MockMvc mockMvc;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService =
                mock(PasswordResetService.class);

        PasswordResetController controller =
                new PasswordResetController(
                        passwordResetService
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    /* ==================== 인증번호 발송 테스트 ==================== */

    /**
     * 정상 이메일 인증번호 발송 요청.
     */
    @Test
    void shouldSendResetCode() throws Exception {
        when(passwordResetService.sendCode(
                any(PasswordResetCodeRequest.class)
        )).thenReturn(
                new PasswordResetCodeResponse(
                        "가입된 이메일인 경우 인증번호가 발송됩니다.",
                        300
                )
        );

        mockMvc.perform(
                        post(BASE_URL + "/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "test@example.com"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "가입된 이메일인 경우 인증번호가 발송됩니다."
                ))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(header().string(
                        "Cache-Control",
                        "no-store"
                ));

        verify(passwordResetService).sendCode(
                new PasswordResetCodeRequest(EMAIL)
        );
    }

    /**
     * 이메일이 누락되면 인증번호 발송을 제한한다.
     */
    @Test
    void shouldRejectMissingEmail() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 잘못된 이메일 형식은 허용하지 않는다.
     */
    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "invalid-email"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /* ==================== 인증번호 검증 테스트 ==================== */

    /**
     * 정상 인증번호 검증 시 재설정 토큰을 반환한다.
     */
    @Test
    void shouldVerifyResetCode() throws Exception {
        when(passwordResetService.verifyCode(
                any(PasswordResetVerifyRequest.class)
        )).thenReturn(
                new PasswordResetVerifyResponse(
                        RESET_TOKEN,
                        600
                )
        );

        mockMvc.perform(
                        post(BASE_URL + "/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "test@example.com",
                                    "code": "012345"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken")
                        .value(RESET_TOKEN))
                .andExpect(jsonPath("$.expiresIn")
                        .value(600))
                .andExpect(header().string(
                        "Cache-Control",
                        "no-store"
                ));

        verify(passwordResetService).verifyCode(
                new PasswordResetVerifyRequest(
                        EMAIL,
                        "012345"
                )
        );
    }

    /**
     * 인증번호는 숫자 6자리여야 한다.
     */
    @Test
    void shouldRejectInvalidCodeLength() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "test@example.com",
                                    "code": "12345"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 인증번호에 문자가 포함되면 검증을 제한한다.
     */
    @Test
    void shouldRejectNonNumericCode() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "test@example.com",
                                    "code": "ABC123"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 인증번호가 일치하지 않거나 만료된 경우
     * 400 응답을 반환한다.
     */
    @Test
    void shouldRejectIncorrectCode() throws Exception {
        when(passwordResetService.verifyCode(
                any(PasswordResetVerifyRequest.class)
        )).thenThrow(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "인증번호가 올바르지 않거나 만료되었습니다."
                )
        );

        mockMvc.perform(
                        post(BASE_URL + "/verify-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "test@example.com",
                                    "code": "999999"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verify(passwordResetService).verifyCode(
                new PasswordResetVerifyRequest(
                        EMAIL,
                        "999999"
                )
        );
    }

    /* ==================== 비밀번호 재설정 테스트 ==================== */

    /**
     * 정상 비밀번호 재설정 시 204 응답을 반환한다.
     */
    @Test
    void shouldResetPassword() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "resetToken": "test-reset-token",
                                    "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        "Cache-Control",
                        "no-store"
                ));

        verify(passwordResetService).resetPassword(
                new PasswordResetRequest(
                        RESET_TOKEN,
                        "NewPassword123!"
                )
        );
    }

    /**
     * 재설정 토큰이 없으면 비밀번호를 변경할 수 없다.
     */
    @Test
    void shouldRejectMissingResetToken() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 8자 미만의 비밀번호는 허용하지 않는다.
     */
    @Test
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(
                        post(BASE_URL + "/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "resetToken": "test-reset-token",
                                    "newPassword": "1234567"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 64자를 초과하는 비밀번호는 허용하지 않는다.
     */
    @Test
    void shouldRejectLongPassword() throws Exception {
        String longPassword = "a".repeat(65);

        mockMvc.perform(
                        post(BASE_URL + "/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "resetToken": "test-reset-token",
                                    "newPassword": "%s"
                                }
                                """.formatted(longPassword))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    /**
     * 유효하지 않거나 만료된 재설정 토큰은
     * 비밀번호 변경에 사용할 수 없다.
     */
    @Test
    void shouldRejectInvalidResetToken() throws Exception {
        doThrow(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "유효하지 않거나 만료된 재설정 토큰입니다."
                )
        ).when(passwordResetService)
                .resetPassword(any(PasswordResetRequest.class));

        mockMvc.perform(
                        post(BASE_URL + "/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "resetToken": "invalid-token",
                                    "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verify(passwordResetService).resetPassword(
                new PasswordResetRequest(
                        "invalid-token",
                        "NewPassword123!"
                )
        );
    }
}