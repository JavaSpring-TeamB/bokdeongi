package com.bokdeongi.backend.auth.controller;

import com.bokdeongi.backend.auth.dto.request.KakaoReconsentRequest;
import com.bokdeongi.backend.auth.dto.response.KakaoReconsentResponse;
import com.bokdeongi.backend.auth.dto.response.LoginResponse;
import com.bokdeongi.backend.auth.dto.response.LoginResult;
import com.bokdeongi.backend.auth.entity.User;
import com.bokdeongi.backend.auth.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ReflectionTestUtils.setField(
                authController,
                "refreshTokenExpiration",
                604800000L
        );
    }

    @Test
    void 카카오_재동의에_성공하면_인증_쿠키와_온보딩_정보를_반환한다() {
        User user = mock(User.class);

        when(user.getId()).thenReturn(1L);
        when(user.getProfileCompletedAt()).thenReturn(null);

        KakaoReconsentRequest request =
                mock(KakaoReconsentRequest.class);

        MockHttpServletRequest httpRequest =
                new MockHttpServletRequest();

        MockHttpSession session = new MockHttpSession();

        session.setAttribute("KAKAO_RECONSENT_ID", "12345");
        session.setAttribute(
                "KAKAO_RECONSENT_CREATED_AT",
                System.currentTimeMillis()
        );

        httpRequest.setSession(session);

        when(authService.kakaoReconsent(request, httpRequest))
                .thenReturn(user);

        LoginResult loginResult = new LoginResult(
                new LoginResponse(
                        "test-access-token",
                        "Bearer",
                        1800L
                ),
                "test-refresh-token"
        );

        when(authService.issueTokens(1L))
                .thenReturn(loginResult);

        ResponseEntity<KakaoReconsentResponse> response =
                authController.kakaoReconsent(
                        request, httpRequest
                );

        // HTTP 응답 상태 확인
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // 응답 데이터 확인
        KakaoReconsentResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(1L, body.userId());
        assertTrue(body.profileOnboarding());

        // 인증 쿠키 확인
        List<String> cookies =
                response.getHeaders().get(HttpHeaders.SET_COOKIE);

        assertNotNull(cookies);
        assertEquals(2, cookies.size());

        assertTrue(cookies.stream().anyMatch(
                cookie -> cookie.startsWith(
                        "accessToken=test-access-token"
                )
        ));

        assertTrue(cookies.stream().anyMatch(
                cookie -> cookie.startsWith(
                        "refreshToken=test-refresh-token"
                )
        ));

        // 재동의 임시 세션 제거 확인
        assertTrue(session.isInvalid());

        // JWT 발급 확인
        verify(authService).issueTokens(1L);
    }
}