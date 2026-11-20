package com.bokdeongi.backend.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthFrontendRedirectUrlTest {

    @Test
    void shouldBuildOnlyFixedOAuthResultUrls() {
        OAuthFrontendRedirectUrl redirectUrl = new OAuthFrontendRedirectUrl(
                "http://localhost:63342/index.html",
                "http://localhost:63342"
        );

        assertEquals(
                "http://localhost:63342/index.html",
                redirectUrl.home()
        );
        assertEquals(
                "http://localhost:63342/index.html?login=failed",
                redirectUrl.loginFailed()
        );
        assertEquals(
                "http://localhost:63342/index.html?kakaoSignup=true",
                redirectUrl.kakaoSignupRequired()
        );
    }

    @Test
    void shouldRejectRedirectUrlOutsideAllowedOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthFrontendRedirectUrl(
                        "https://untrusted.example/index.html",
                        "http://localhost:63342"
                )
        );
    }

    @Test
    void shouldRejectConfiguredQueryOrFragment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OAuthFrontendRedirectUrl(
                        "http://localhost:63342/index.html?next=https://untrusted.example",
                        "http://localhost:63342"
                )
        );
    }
}
