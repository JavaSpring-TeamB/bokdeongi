package com.bokdeongi.backend.auth.oauth;

import com.bokdeongi.backend.auth.dto.response.LoginResult;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 카카오 OAuth2 로그인 성공 처리 핸들러.
 *
 * 신규 회원은 카카오 정보를 세션에 저장한 뒤 약관 동의 화면으로 이동하고,
 * 기존 회원은 JWT를 발급하여 인증 쿠키를 설정한다.
 */
@Component
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final OAuthFrontendRedirectUrl frontendRedirectUrl;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            AuthService authService,
            OAuthFrontendRedirectUrl frontendRedirectUrl
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    /**
     * 카카오 인증 성공 후 신규 회원과 기존 회원을 구분한다.
     */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oauth2User =
                (OAuth2User) authentication.getPrincipal();

        // 카카오 고유 ID 추출
        Object kakaoIdValue = oauth2User.getAttribute("id");

        if (kakaoIdValue == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Kakao user ID is missing"
            );
            return;
        }

        String kakaoId = String.valueOf(kakaoIdValue);

        // 카카오 ID를 기준으로 가입 여부 확인
        Optional<User> existingUser =
                userRepository.findByKakaoId(kakaoId);

        if (existingUser.isEmpty()) {
            // 신규 회원: 약관 동의 전 임시 가입 정보 저장
            saveSignupSession(request, oauth2User, kakaoId);

            response.sendRedirect(
                    frontendRedirectUrl.kakaoSignupRequired()
            );
            return;
        }

        // 기존 회원: JWT 발급 및 인증 쿠키 설정
        User user = existingUser.get();
        LoginResult loginResult = authService.issueTokens(user.getId());

        setTokenCookies(response, loginResult);

        // OAuth2 로그인 과정에서 사용한 세션 제거
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        // OAuth2 인증 컨텍스트 정리
        SecurityContextHolder.clearContext();

        // 로그인 완료 후 메인 화면으로 이동
        response.sendRedirect(frontendRedirectUrl.home());
    }

    /**
     * 신규 카카오 회원의 정보를 서버 세션에 임시 저장한다.
     */
    private void saveSignupSession(
            HttpServletRequest request,
            OAuth2User oauth2User,
            String kakaoId
    ) {
        KakaoProfile profile = extractKakaoProfile(oauth2User);

        HttpSession session = request.getSession(true);

        session.setAttribute("KAKAO_SIGNUP_ID", kakaoId);
        session.setAttribute("KAKAO_SIGNUP_NICKNAME", profile.nickname());
        session.setAttribute("KAKAO_SIGNUP_EMAIL", profile.email());
        session.setAttribute(
                "KAKAO_SIGNUP_CREATED_AT",
                System.currentTimeMillis()
        );
    }

    /**
     * 카카오 계정 정보에서 이메일과 닉네임을 추출한다.
     * 제공되지 않은 값은 null로 유지한다.
     */
    private KakaoProfile extractKakaoProfile(OAuth2User oauth2User) {
        String email = null;
        String nickname = null;

        Object kakaoAccountValue =
                oauth2User.getAttribute("kakao_account");

        if (kakaoAccountValue instanceof Map<?, ?> kakaoAccount) {

            Object emailValue = kakaoAccount.get("email");

            if (emailValue instanceof String emailString) {
                email = emailString;
            }

            Object profileValue = kakaoAccount.get("profile");

            if (profileValue instanceof Map<?, ?> profile) {
                Object nicknameValue = profile.get("nickname");

                if (nicknameValue instanceof String nicknameString) {
                    nickname = nicknameString;
                }
            }
        }

        return new KakaoProfile(email, nickname);
    }

    /**
     * 기존 카카오 회원의 Access Token과 Refresh Token을
     * HttpOnly 쿠키로 전달한다.
     */
    private void setTokenCookies(
            HttpServletResponse response,
            LoginResult loginResult
    ) {
        String accessToken =
                loginResult.loginResponse().accessToken();

        String refreshToken = loginResult.refreshToken();

        // Access Token 쿠키
        ResponseCookie accessCookie =
                ResponseCookie.from("accessToken", accessToken)
                        .httpOnly(true)
                        .secure(false) // 로컬 HTTP 개발 환경용
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(Duration.ofMinutes(30))
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
        );

        // Refresh Token 쿠키
        ResponseCookie refreshCookie =
                ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .secure(false) // 로컬 HTTP 개발 환경용
                        .sameSite("Lax")
                        .path("/api/auth")
                        .maxAge(Duration.ofDays(7))
                        .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
        );
    }

    /**
     * 카카오 계정에서 추출한 임시 프로필 정보.
     */
    private record KakaoProfile(
            String email,
            String nickname
    ) {
    }
}
