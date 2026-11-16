package com.bokdeongi.backend.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}