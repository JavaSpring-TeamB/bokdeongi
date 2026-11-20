package com.bokdeongi.backend.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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

    private final OAuthFrontendRedirectUrl frontendRedirectUrl;

    public OAuth2LoginFailureHandler(
            OAuthFrontendRedirectUrl frontendRedirectUrl
    ) {
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    /**
     * 카카오 인증 실패 시 실행된다.
     */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        String oauth2ErrorCode =
                exception instanceof OAuth2AuthenticationException oauth2Exception
                        ? oauth2Exception.getError().getErrorCode()
                        : "not_available";

        // 인증 코드·state·토큰·세션·개인정보 없이 실패 분류만 기록
        log.warn(
                "카카오 로그인 실패: stage=oauth2-callback, type={}, oauth2Error={}",
                exception.getClass().getSimpleName(),
                oauth2ErrorCode
        );

        // 로그인 실패 결과를 프론트엔드에 전달
        response.sendRedirect(
                frontendRedirectUrl.loginFailed()
        );
    }
}
