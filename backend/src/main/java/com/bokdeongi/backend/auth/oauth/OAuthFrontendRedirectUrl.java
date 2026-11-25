package com.bokdeongi.backend.auth.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth 완료 후 이동할 프론트 주소를 검증하고 고정된 결과 경로를 만든다.
 * 리다이렉트 대상은 요청값이 아니라 서버 설정값으로만 결정한다.
 */
@Component
public class OAuthFrontendRedirectUrl {

    private final String frontendHomeUrl;

    public OAuthFrontendRedirectUrl(
            @Value("${app.oauth.frontend.home-url:http://localhost:63342/bokdeongi/frontend/index.html}")
            String frontendHomeUrl,
            @Value("${app.oauth.frontend.allowed-origins:http://localhost:63342}")
            String allowedOrigins
    ) {
        URI home = parseHomeUrl(frontendHomeUrl);
        Set<String> allowedOriginSet = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(OAuthFrontendRedirectUrl::parseOrigin)
                .collect(Collectors.toUnmodifiableSet());

        if (!allowedOriginSet.contains(originOf(home))) {
            throw new IllegalArgumentException(
                    "OAuth 프론트 리다이렉트 Origin이 허용 목록에 없습니다."
            );
        }

        this.frontendHomeUrl = home.toASCIIString();
    }

    public String home() {
        return frontendHomeUrl;
    }

    public String loginFailed() {
        return frontendHomeUrl + "?login=failed";
    }

    public String kakaoSignupRequired() {
        return frontendHomeUrl + "?kakaoSignup=true";
    }

    /**
     * 기존 카카오 회원의 필수 약관 재동의 화면으로 이동한다.
     */
    public String kakaoReconsentRequired() {
        return frontendHomeUrl + "?kakaoReconsent=true";
    }

    private static URI parseHomeUrl(String value) {
        try {
            URI uri = new URI(value);

            if (!isHttpUrl(uri)
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(
                        "OAuth 프론트 리다이렉트 URL 형식이 올바르지 않습니다."
                );
            }

            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "OAuth 프론트 리다이렉트 URL 형식이 올바르지 않습니다.", e
            );
        }
    }

    private static String parseOrigin(String value) {
        try {
            URI uri = new URI(value);

            if (!isHttpUrl(uri)
                    || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(
                        "OAuth 프론트 허용 Origin 형식이 올바르지 않습니다."
                );
            }

            return originOf(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "OAuth 프론트 허용 Origin 형식이 올바르지 않습니다.", e
            );
        }
    }

    private static boolean isHttpUrl(URI uri) {
        return ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
    }

    private static String originOf(URI uri) {
        return uri.getScheme().toLowerCase()
                + "://"
                + uri.getHost().toLowerCase()
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
