package com.bokdeongi.backend.auth.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CsrfController {

    /**
     * CSRF 토큰 발급
     * 클라이언트가 POST, PATCH, DELETE 등의 요청에서
     * 사용할 CSRF 토큰과 헤더 이름을 반환한다.
     */
    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "token", csrfToken.getToken()
        );
    }
}