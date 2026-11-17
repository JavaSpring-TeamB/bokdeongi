package com.bokdeongi.backend.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 카카오 OAuth2 로그인 실패 처리 핸들러.
 *
 * 인증 실패 원인을 서버 로그에 기록하고
 * 프론트엔드의 로그인 실패 화면으로 이동시킨다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    /**
     * 카카오 인증 실패 시 실행된다.
     */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        // 인증 실패 유형과 원인을 서버 로그에 기록
        log.warn(
                "카카오 로그인 실패: {}",
                exception.getClass().getSimpleName()
        );

        log.debug("카카오 로그인 실패 상세", exception);

        // 로그인 실패 결과를 프론트엔드에 전달
        response.sendRedirect(
                "http://localhost:63342/bokdeongi/frontend/index.html?login=failed"
        );
    }
}