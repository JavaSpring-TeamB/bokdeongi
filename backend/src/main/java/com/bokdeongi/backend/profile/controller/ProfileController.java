package com.bokdeongi.backend.profile.controller;

import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import com.bokdeongi.backend.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.bokdeongi.backend.profile.dto.request.ProfileStep1Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep2Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep3Request;
import com.bokdeongi.backend.profile.dto.request.ProfileStep4Request;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 회원 프로필 관리 API 컨트롤러.
 *
 * 로그인한 회원의 프로필 조회, 단계별 정보 저장 및
 * 프로필 최종 완료 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/me/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * PROF-01: 로그인한 회원의 프로필 조회.
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(
            Authentication authentication
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response = profileService.getProfile(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * PROF-01: 프로필 1단계 기본 정보 저장.
     *
     * 생년월일, 성별, 거주지를 저장한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/step1")
    public ResponseEntity<ProfileResponse> saveProfileStep1(
            Authentication authentication,
            @Valid @RequestBody ProfileStep1Request request
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response =
                profileService.saveProfileStep1(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * PROF-01: 프로필 2단계 가구 정보 저장.
     *
     * 가구원 수, 소득, 주거 형태, 무주택 여부,
     * 가구 유형을 저장한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/step2")
    public ResponseEntity<ProfileResponse> saveProfileStep2(
            Authentication authentication,
            @Valid @RequestBody ProfileStep2Request request
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response =
                profileService.saveProfileStep2(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * PROF-01: 프로필 3단계 나의 상황 정보 저장.
     *
     * 현재 상황, 혼인 상태, 자녀 정보,
     * 복지 수급 상태를 저장한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/step3")
    public ResponseEntity<ProfileResponse> saveProfileStep3(
            Authentication authentication,
            @Valid @RequestBody ProfileStep3Request request
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response =
                profileService.saveProfileStep3(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * PROF-01,04: 프로필 4단계 특수 상황 정보 저장.
     *
     * 민감정보 수집 동의 여부를 확인하고,
     * 동의한 경우 특수 상황 정보를 암호화하여 저장한다.
     *
     * null은 입력 건너뛰기, 빈 목록([])은 해당 없음을 의미한다.
     * 최초 동의 이력이 없는 경우에만 동의 상태를 기록한다.
     * 이후 동의 변경 및 철회는 약관 동의 변경 API에서 처리한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/step4")
    public ResponseEntity<ProfileResponse> saveProfileStep4(
            Authentication authentication,
            @Valid @RequestBody ProfileStep4Request request
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response =
                profileService.saveProfileStep4(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * PROF-01: 프로필 최종 완료 처리.
     *
     * 필수 입력 단계(1~3단계)가 완료되었는지 확인하고,
     * 프로필 완료 시각을 기록한다.
     *
     * 4단계 민감정보 입력은 선택 사항이다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/complete")
    public ResponseEntity<ProfileResponse> completeProfile(
            Authentication authentication
    ) {
        Long userId = getAuthenticatedUserId(authentication);

        ProfileResponse response =
                profileService.completeProfile(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 기존 AUTH와 동일한 방식으로 회원 ID 추출.
     */
    private Long getAuthenticatedUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
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