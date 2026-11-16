package com.bokdeongi.backend.auth.controller;

import com.bokdeongi.backend.auth.dto.request.SignupRequest;
import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import com.bokdeongi.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bokdeongi.backend.auth.dto.request.LoginRequest;
import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.dto.request.KakaoSignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.web.bind.annotation.RequestHeader;
import com.bokdeongi.backend.auth.dto.response.LoginResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = authService.login(request);

        ResponseCookie refreshCookie = ResponseCookie.from(
                        "refreshToken",
                        result.refreshToken()
                )
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경용
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration / 1000)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.loginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken
    ) {
        LoginResult result = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from(
                        "refreshToken",
                        result.refreshToken()
                )
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경용
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration / 1000)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.loginResponse());
    }

    @PostMapping("/kakao/signup")
    public ResponseEntity<SignupResponse> kakaoSignup(
            @Valid @RequestBody KakaoSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        SignupResponse response = authService.kakaoSignup(
                request,
                httpRequest
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/kakao/csrf")
    public ResponseEntity<CsrfToken> kakaoCsrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken);
    }

    /* 선택 개인정보 및 민감정보 동의 변경·철회 */
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/consents")
    public ResponseEntity<Void> updateConsent(
            @AuthenticationPrincipal Long userId,

            @Parameter(
                    name = "X-XSRF-TOKEN",
                    description = "CSRF 토큰",
                    required = true,
                    in = ParameterIn.HEADER
            )
            @RequestHeader("X-XSRF-TOKEN") String csrfToken,

            @Valid @RequestBody ConsentUpdateRequest request
    ) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        authService.updateConsent(userId, request);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Long userId;

        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보를 확인할 수 없습니다."
            );
        }

        authService.logout(userId);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 로컬 개발 환경
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }
}
