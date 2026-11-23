package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.recommend.RecommendController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    // 로그인한 사용자의 신청 현황 목록 조회
    @GetMapping
    @Operation(summary = "내 신청 현황 목록 조회")
    public ApplicationListResponse getList(
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        return service.getList(userId);
    }

    // 신청 시작 또는 기존 신청 이어하기
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "신청 시작 또는 이어하기")
    public ApplicationResponse start(
            @Valid @RequestBody ApplicationStartRequest request,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        return service.start(userId, request.benefitId());
    }

    // 신청 건 상세 조회 및 이어하기
    @GetMapping("/{id}")
    @Operation(summary = "신청 건 조회 및 이어하기")
    public ApplicationResponse get(
            @PathVariable long id,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        return service.get(userId, id);
    }

    // 신청 진행 단계 및 답변 자동 저장
    @PatchMapping("/{id}")
    @Operation(summary = "신청 진행 상태 자동 저장")
    public ApplicationResponse saveProgress(
            @PathVariable long id,
            @Valid @RequestBody ApplicationSaveRequest request,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        return service.saveProgress(userId, id, request);
    }

    // 사용자가 직접 신청 완료 및 선정·미선정 결과 기록
    @PatchMapping("/{id}/status")
    @Operation(summary = "신청 완료 및 결과 상태 변경")
    public ApplicationResponse updateStatus(
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody ApplicationStatusUpdateRequest request
    ) {
        long userId = RecommendController.userId(authentication);

        return service.updateStatus(userId, id, request);
    }
}