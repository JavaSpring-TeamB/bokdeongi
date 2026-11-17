package com.bokdeongi.backend.auth.jwt;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 기반 사용자 인증 필터.
 *
 * 요청의 Authorization 헤더 또는 accessToken 쿠키에서 JWT를 조회하고,
 * 토큰 검증 및 회원 조회를 거쳐 Spring Security 인증 정보를 설정한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * 토큰 재발급 API는 Access Token 인증 필터를 적용하지 않는다.
     * Refresh Token 검증은 AuthService에서 별도로 수행한다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/refresh".equals(request.getServletPath());
    }

    /**
     * 요청에 포함된 Access Token을 검증하고 인증 정보를 등록한다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Authorization 헤더에서 Access Token 조회
        String authorization = request.getHeader("Authorization");
        String token = null;

        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }

        // 2. 헤더에 토큰이 없으면 accessToken 쿠키 확인
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        // 3. 토큰이 없는 요청은 인증 정보를 설정하지 않고 다음 필터로 전달
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Access Token 서명, 만료 시간 및 토큰 유형 검증
        if (!jwtTokenProvider.validateAccessToken(token)) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid Access Token"
            );
            return;
        }

        try {
            // 5. JWT에 포함된 회원 ID로 DB 조회
            Long userId = jwtTokenProvider.getUserId(token);

            User user = userRepository.findById(userId).orElse(null);

            // 탈퇴 등으로 회원이 존재하지 않으면 인증 거부
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // 6. DB의 토큰 버전과 JWT의 토큰 버전 비교
            Integer tokenVersion = jwtTokenProvider.getTokenVersion(token);

            if (tokenVersion == null
                    || !tokenVersion.equals(user.getTokenVersion())) {
                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Token has been revoked"
                );
                return;
            }

            // 7. 회원 권한을 Spring Security 권한 형식으로 변환
            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole())
            );

            // 8. 인증 객체 생성 (Principal: 회원 ID)
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getId(),
                    null,
                    authorities
            );

            // 9. 현재 요청의 SecurityContext에 인증 정보 등록
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (RuntimeException e) {
            // 인증 처리 중 오류가 발생하면 인증 정보를 제거하고 401 반환
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 10. 인증 처리가 완료되면 다음 필터로 전달
        filterChain.doFilter(request, response);
    }
}