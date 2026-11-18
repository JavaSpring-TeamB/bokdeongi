package com.bokdeongi.backend.profile.controller;

import com.bokdeongi.backend.profile.dto.response.ProfileAnswerResponse;
import com.bokdeongi.backend.profile.service.ProfileAnswerService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * PROF-06: 추가 질문 답변 조회 API.
 *
 * 답변 저장 및 수정은 BEN-04의
 * PUT /api/me/answers API를 사용한다.
 */
@RestController
@RequestMapping("/api/me/profile/answers")
public class ProfileAnswerController {

    private final ProfileAnswerService profileAnswerService;

    public ProfileAnswerController(
            ProfileAnswerService profileAnswerService
    ) {
        this.profileAnswerService = profileAnswerService;
    }

    /**
     * 저장된 추가 질문 답변 전체 조회.
     * 마이페이지에서 활용한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<ProfileAnswerResponse>> getAnswers(
            Authentication authentication
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                profileAnswerService.getAnswers(userId)
        );
    }

    /**
     * 특정 질문에 저장된 답변 조회.
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{key}")
    public ResponseEntity<ProfileAnswerResponse> getAnswer(
            Authentication authentication,
            @PathVariable String key
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                profileAnswerService.getAnswer(userId, key)
        );
    }

    private Long getAuthenticatedUserId(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "인증 정보를 확인할 수 없습니다."
            );
        }
    }
}