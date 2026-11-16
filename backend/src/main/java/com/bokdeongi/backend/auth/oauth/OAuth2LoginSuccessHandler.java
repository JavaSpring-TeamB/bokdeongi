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

        // 카카오 ID로 복덩이 회원 조회
        User user = userRepository.findByKakaoId(kakaoId)
                .orElse(null);

        if (user == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Kakao user not found"
            );
            return;
        }

        // 기존 JWT 발급 기능 재사용
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(false) // 로컬 HTTP 개발 환경에서만
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // OAuth2 인증에 사용한 세션 제거
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        // 현재 요청의 인증 정보 제거
        SecurityContextHolder.clearContext();

        // 프론트엔드로 이동
        response.sendRedirect("http://localhost:63342/bokdeongi/frontend/index.html");
    }
}