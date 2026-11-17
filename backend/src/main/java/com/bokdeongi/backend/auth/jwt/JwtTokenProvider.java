package com.bokdeongi.backend.auth.jwt;

import com.bokdeongi.backend.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급 및 검증을 담당하는 컴포넌트.
 *
 * Access Token과 Refresh Token을 생성하고,
 * JWT 서명, 만료 시간, 토큰 유형 및 클레임을 검증한다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    /**
     * JWT 서명 키와 토큰별 유효기간을 설정한다.
     * 유효기간 설정값의 단위는 밀리초이다.
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(secret)
        );
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * Access Token 발급.
     * 회원 ID, 권한, 토큰 버전 및 만료 시간을 포함한다.
     */
    public String generateAccessToken(User user) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("type", "access")
                .claim("role", user.getRole())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * Refresh Token 발급.
     * 재발급 시마다 고유한 JWT ID를 생성한다.
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .claim("tokenVersion", user.getTokenVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * Access Token 유효성 검증.
     * 서명, 만료 시간 및 토큰 유형이 올바른지 확인한다.
     */
    public boolean validateAccessToken(String token) {
        return validateTokenType(token, "access");
    }

    /**
     * Refresh Token 유효성 검증.
     * 서명, 만료 시간 및 토큰 유형이 올바른지 확인한다.
     */
    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, "refresh");
    }

    /**
     * JWT 기본 유효성 검증.
     * 서명과 만료 시간 등을 검증하며 토큰 유형은 구분하지 않는다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * JWT에 포함된 회원 ID를 추출한다.
     */
    public Long getUserId(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.valueOf(subject);
    }

    /**
     * JWT에 포함된 토큰 버전을 추출한다.
     */
    public Integer getTokenVersion(String token) {
        return parseClaims(token).get("tokenVersion", Integer.class);
    }

    /**
     * JWT 유형별 유효성 검증 공통 메서드.
     * Access Token과 Refresh Token의 중복 검증 로직을 통합한다.
     */
    private boolean validateTokenType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * JWT 파싱 공통 메서드.
     * 서명을 검증하고 JWT의 Claims를 반환한다.
     * 유효하지 않거나 만료된 토큰은 예외가 발생한다.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}