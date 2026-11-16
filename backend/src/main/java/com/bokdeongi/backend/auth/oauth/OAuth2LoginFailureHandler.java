package com.bokdeongi.backend.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        System.out.println(
                "카카오 로그인 실패 코드: " +
                        exception.getClass().getSimpleName()
        );

        System.out.println(
                "카카오 로그인 실패 원인: " +
                        exception.getMessage()
        );

        // 로그인 실패 시 프론트엔드로 이동
        response.sendRedirect(
                "http://localhost:63342/bokdeongi/frontend/index.html?login=failed"
        );
    }
}