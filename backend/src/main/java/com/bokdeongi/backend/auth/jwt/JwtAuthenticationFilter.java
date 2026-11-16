package com.bokdeongi.backend.auth.jwt;

import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.http.Cookie;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Authorization 헤더에서 JWT 확인
        String authorization = request.getHeader("Authorization");
        String token = null;

        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        }

        // 2. 헤더에 JWT가 없으면 쿠키에서 확인
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        // 3. JWT가 없으면 다음 필터로 이동
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // JWT가 유효하지 않으면 401 반환
        if (!jwtTokenProvider.validateToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT");
            return;
        }

        try {
            // JWT에서 사용자 ID 추출 후 DB 조회
            Long userId = jwtTokenProvider.getUserId(token);

            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // 사용자 권한 설정
            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole())
            );

            // Spring Security 인증 객체 생성
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getId(),
                    null,
                    authorities
            );

            // 현재 요청의 인증 정보 등록
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}