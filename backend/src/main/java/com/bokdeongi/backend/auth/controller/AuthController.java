package com.bokdeongi.backend.auth.controller;

import com.bokdeongi.backend.auth.dto.request.ConsentUpdateRequest;
import com.bokdeongi.backend.auth.dto.request.KakaoSignupRequest;
import com.bokdeongi.backend.auth.dto.request.LoginRequest;
import com.bokdeongi.backend.auth.dto.request.SignupRequest;
import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.dto.response.LoginResult;
import com.bokdeongi.backend.auth.dto.response.SignupResponse;
import com.bokdeongi.backend.auth.dto.response.ConsentStatusResponse;
import com.bokdeongi.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.bokdeongi.backend.auth.dto.request.PasswordChangeRequest;
import com.bokdeongi.backend.auth.dto.response.EmailAvailabilityResponse;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    // Refresh Token 쿠키 유효기간 (밀리초)
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 일반 회원가입
     * 회원 정보를 검증하고 신규 계정을 생성한다.
     */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 회원가입 이메일 사용 가능 여부를 조회한다.
     *
     * @param email 중복 확인할 이메일 주소
     * @return 이메일 사용 가능 여부
     */
    @GetMapping("/email-availability")
    public ResponseEntity<EmailAvailabilityResponse> checkEmailAvailability(
            @RequestParam String email
    ) {
        boolean available = authService.isEmailAvailable(email);

        return ResponseEntity.ok(
                new EmailAvailabilityResponse(available)
        );
    }

    /**
     * 일반 로그인
     * 이메일과 비밀번호를 검증하고 Access Token과
     * Refresh Token을 발급한다.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = authService.login(request);

        // Refresh Token은 HttpOnly 쿠키로 전달
        ResponseCookie refreshCookie =
                createRefreshTokenCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.loginResponse());
    }


    /**
     * 로그인한 이메일 회원의 비밀번호 변경.
     *
     * 현재 비밀번호 검증 후 새 비밀번호로 변경한다.
     * 변경 성공 시 Refresh Token 쿠키를 만료시킨다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        // 1. 인증된 회원 ID 확인
        Long userId = getAuthenticatedUserId(authentication);

        // 2. 비밀번호 변경
        authService.changePassword(userId, request);

        // 3. 기존 Refresh Token 쿠키 만료
        ResponseCookie refreshCookie =
                expireCookie("refreshToken", "/api/auth");

        // 4. 카카오 로그인 등에서 사용한 Access Token 쿠키도 만료
        ResponseCookie accessCookie =
                expireCookie("accessToken", "/");

        // 5. 변경 성공 후 재로그인 필요
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie.toString(),
                        accessCookie.toString()
                )
                .build();
    }

    /**
     * 토큰 재발급
     * Refresh Token을 검증하고 새로운 토큰을 발급한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = "refreshToken", required = false)
            String refreshToken
    ) {
        LoginResult result = authService.refresh(refreshToken);

        // 재발급된 Refresh Token으로 기존 쿠키 갱신
        ResponseCookie refreshCookie =
                createRefreshTokenCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.loginResponse());
    }

    /**
     * 카카오 회원가입
     * 카카오 인증 후 세션에 저장된 임시 가입 정보를 이용해
     * 신규 회원을 생성한다.
     */
    @PostMapping("/kakao/signup")
    public ResponseEntity<SignupResponse> kakaoSignup(
            @Valid @RequestBody KakaoSignupRequest request,
            HttpServletRequest httpRequest
    ) {
        SignupResponse response =
                authService.kakaoSignup(request, httpRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 카카오 회원가입 과정에서 사용하는 CSRF 토큰을 반환한다.
     */
    @GetMapping("/kakao/csrf")
    public ResponseEntity<CsrfToken> kakaoCsrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(csrfToken);
    }

    /**
     * 로그인한 회원의 선택 개인정보, 민감정보, 서류 기억 및
     * OCR 서류 처리 동의 상태를 조회한다.
     *
     * @param authentication 인증된 사용자 정보
     * @return 동의 유형별 최신 상태
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/consents")
    public ResponseEntity<ConsentStatusResponse> getConsentStatus(
            Authentication authentication
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ConsentStatusResponse response =
                authService.getConsentStatus(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 선택 개인정보, 민감정보, 서류 기억 및 OCR 서류 처리 동의 변경·철회
     * 기존 동의 기록은 유지하고 새로운 동의 이력을 저장한다.
     */
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
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        authService.updateConsent(userId, request);

        return ResponseEntity.noContent().build();
    }

    /**
     * 로그아웃
     * Refresh Token을 무효화하고 토큰 버전을 증가시켜
     * 기존 Access Token도 사용할 수 없도록 처리한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {

        // 인증된 회원 ID 확인
        Long userId = getAuthenticatedUserId(authentication);

        // 서버에 저장된 인증 정보 무효화
        authService.logout(userId);

        // Access Token 쿠키 만료
        ResponseCookie accessCookie =
                expireCookie("accessToken", "/");

        // Refresh Token 쿠키 만료
        ResponseCookie refreshCookie =
                expireCookie("refreshToken", "/api/auth");

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }

    /**
     * 회원 탈퇴 및 개인정보 삭제
     * 회원 정보를 삭제하고 DB 외래 키의 ON DELETE CASCADE를 통해
     * 연결된 약관 동의 및 서비스 이용 데이터를 함께 삭제한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(Authentication authentication) {

        // 인증된 회원 ID 확인
        Long userId = getAuthenticatedUserId(authentication);

        // 회원 및 관련 데이터 삭제
        authService.withdraw(userId);

        // 탈퇴 후 브라우저에 남은 인증 쿠키 만료
        ResponseCookie refreshCookie =
                expireCookie("refreshToken", "/api/auth");

        ResponseCookie accessCookie =
                expireCookie("accessToken", "/");

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie.toString(),
                        accessCookie.toString()
                )
                .build();
    }

    /**
     * Refresh Token 쿠키 생성
     * 로그인과 토큰 재발급에서 공통으로 사용한다.
     */
    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경용
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(refreshTokenExpiration / 1000)
                .build();
    }

    /**
     * 인증 쿠키 만료
     * 쿠키의 Path를 기존 발급 설정과 일치시켜 삭제한다.
     */
    private ResponseCookie expireCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경용
                .sameSite("Lax")
                .path(path)
                .maxAge(0)
                .build();
    }

    /**
     * 인증된 회원 ID 추출
     * 로그아웃과 회원 탈퇴에서 공통으로 사용한다.
     */
    private Long getAuthenticatedUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보를 확인할 수 없습니다."
            );
        }
    }
}