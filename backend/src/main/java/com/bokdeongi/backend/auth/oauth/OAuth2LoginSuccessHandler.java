package com.bokdeongi.backend.auth.oauth;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.jwt.JwtTokenProvider;
import com.bokdeongi.backend.auth.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.Map;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

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

        // 카카오 ID로 기존 회원 조회
        Optional<User> existingUser = userRepository.findByKakaoId(kakaoId);

        // 신규 카카오 회원
        if (existingUser.isEmpty()) {

            // 카카오 계정 정보 추출
            String nickname = null;
            String email = null;

            Object kakaoAccountValue = oauth2User.getAttribute("kakao_account");

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

            // 카카오 인증 정보를 서버 세션에 임시 저장
            HttpSession session = request.getSession(true);

            session.setAttribute("KAKAO_SIGNUP_ID", kakaoId);
            session.setAttribute("KAKAO_SIGNUP_NICKNAME", nickname);
            session.setAttribute("KAKAO_SIGNUP_EMAIL", email);
            session.setAttribute(
                    "KAKAO_SIGNUP_CREATED_AT",
                    System.currentTimeMillis()
            );

            // 프론트엔드로 이동
            response.sendRedirect(
                    "http://localhost:63342/bokdeongi/frontend/index.html?kakaoSignup=true"
            );
            return;
        }

        // 기존 카카오 회원
        User user = existingUser.get();

        // JWT 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        // JWT 쿠키 설정
        ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(false) // 로컬 개발 환경
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // OAuth2 인증 세션 제거
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        // 인증 정보 제거
        SecurityContextHolder.clearContext();

        // 프론트엔드로 이동
        response.sendRedirect(
                "http://localhost:63342/bokdeongi/frontend/index.html"
        );
    }
}