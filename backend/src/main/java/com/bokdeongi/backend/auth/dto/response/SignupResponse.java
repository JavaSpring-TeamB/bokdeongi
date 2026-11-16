package com.bokdeongi.backend.auth.dto.response;

public record SignupResponse(
        Long userId,
        String email,
        String message
) {
}
