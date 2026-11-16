package com.bokdeongi.backend.auth.dto.response;

public record LoginResult(
        LoginResponse loginResponse,
        String refreshToken
) {
}