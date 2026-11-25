package com.bokdeongi.backend.auth.oauth;

import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.dto.response.LoginResult;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.repository.UserRepository;
import com.bokdeongi.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OAuth2LoginSuccessHandlerTest {

    private UserRepository userRepository;
    private AuthService authService;
    private OAuth2LoginSuccessHandler handler;

    private final String homeUrl =
            "http://localhost:63342/bokdeongi/frontend/index.html";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = mock(AuthService.class);

        OAuthFrontendRedirectUrl redirectUrl =
                new OAuthFrontendRedirectUrl(
                        homeUrl,
                        "http://localhost:63342"
                );

        handler = new OAuth2LoginSuccessHandler(
                userRepository,
                authService,
                redirectUrl
        );
    }

    private Authentication authentication() {
        OAuth2User oauth2User = mock(OAuth2User.class);

        when(oauth2User.getAttribute("id"))
                .thenReturn(12345L);

        return new UsernamePasswordAuthenticationToken(
                oauth2User,
                null,
                List.of()
        );
    }

    private void mockTokenIssuance() {
        LoginResult result = new LoginResult(
                new LoginResponse(
                        "test-access-token",
                        "Bearer",
                        1800L
                ),
                "test-refresh-token"
        );

        when(authService.issueTokens(anyLong()))
                .thenReturn(result);
    }

    @Test
    void 프로필_완료_회원은_홈으로_이동한다() throws Exception {
        User user = mock(User.class);

        when(user.getId()).thenReturn(1L);
        when(user.getProfileCompletedAt())
                .thenReturn(LocalDateTime.now());

        when(userRepository.findByKakaoId("12345"))
                .thenReturn(Optional.of(user));

        // 기존 회원의 필수 약관 동의 완료 상태 설정
        when(authService.hasRequiredConsents(user.getId()))
                .thenReturn(true);

        mockTokenIssuance();

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication()
        );

        assertEquals(homeUrl, response.getRedirectedUrl());

        assertEquals(
                2,
                response.getHeaders(HttpHeaders.SET_COOKIE).size()
        );

        verify(authService).issueTokens(1L);
    }

    @Test
    void 프로필_미완료_회원은_온보딩_힌트를_받는다()
            throws Exception {
        User user = mock(User.class);

        when(user.getId()).thenReturn(2L);
        when(user.getProfileCompletedAt())
                .thenReturn(null);

        when(userRepository.findByKakaoId("12345"))
                .thenReturn(Optional.of(user));

        // 기존 회원의 필수 약관 동의 완료 상태 설정
        when(authService.hasRequiredConsents(user.getId()))
                .thenReturn(true);

        mockTokenIssuance();

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication()
        );

        assertEquals(
                homeUrl + "?profileOnboarding=true",
                response.getRedirectedUrl()
        );

        assertEquals(
                2,
                response.getHeaders(HttpHeaders.SET_COOKIE).size()
        );

        verify(authService).issueTokens(2L);
    }

    @Test
    void 신규_카카오_회원은_기존_가입_경로로_이동한다()
            throws Exception {
        when(userRepository.findByKakaoId("12345"))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request =
                new MockHttpServletRequest();
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication()
        );

        assertEquals(
                homeUrl + "?kakaoSignup=true",
                response.getRedirectedUrl()
        );

        HttpSession session = request.getSession(false);

        assertNotNull(session);
        assertEquals(
                "12345",
                session.getAttribute("KAKAO_SIGNUP_ID")
        );

        verifyNoInteractions(authService);

        assertTrue(
                response.getHeaders(HttpHeaders.SET_COOKIE).isEmpty()
        );
    }

    @Test
    void 필수_약관_미동의_기존_회원은_재동의_화면으로_이동한다()
            throws Exception {

        User user = mock(User.class);

        when(user.getId()).thenReturn(3L);

        when(userRepository.findByKakaoId("12345"))
                .thenReturn(Optional.of(user));

        when(authService.hasRequiredConsents(3L))
                .thenReturn(false);

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                authentication()
        );

        // 재동의 화면 이동 확인
        assertEquals(
                homeUrl + "?kakaoReconsent=true",
                response.getRedirectedUrl()
        );

        // 재동의용 임시 세션 확인
        HttpSession session = request.getSession(false);

        assertNotNull(session);

        assertEquals(
                "12345",
                session.getAttribute("KAKAO_RECONSENT_ID")
        );

        assertNotNull(
                session.getAttribute("KAKAO_RECONSENT_CREATED_AT")
        );

        // 필수 약관 동의 여부 확인
        verify(authService).hasRequiredConsents(3L);

        // 필수 약관 동의 전 JWT 발급 방지 확인
        verify(authService, never()).issueTokens(anyLong());

        // 기존 인증 토큰 무효화 확인
        verify(authService).logout(3L);

        // 기존 인증 쿠키 만료 확인
        var cookies = response.getHeaders(HttpHeaders.SET_COOKIE);

        assertEquals(2, cookies.size());

        assertTrue(cookies.stream().anyMatch(
                cookie -> cookie.startsWith("accessToken=;")
                        && cookie.contains("Max-Age=0")
        ));

        assertTrue(cookies.stream().anyMatch(
                cookie -> cookie.startsWith("refreshToken=;")
                        && cookie.contains("Max-Age=0")
        ));
    }
}