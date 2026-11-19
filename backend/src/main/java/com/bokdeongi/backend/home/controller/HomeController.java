package com.bokdeongi.backend.home.controller;

import com.bokdeongi.backend.home.dto.response.HomeSummaryResponse;
import com.bokdeongi.backend.home.dto.response.HomeActivityResponse;
import com.bokdeongi.backend.home.service.HomeActivityService;
import com.bokdeongi.backend.home.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

/**
 * HOME-01 홈 대시보드 및 HOME-02 활동 요약 API Controller
 *
 * 로그인 사용자의 홈 대시보드 요약 정보를 조회하는 API를 제공한다.
 * 사용자 ID를 인증 정보에서 추출하고 HomeService를 호출하여
 * 프로필, 수령 가능 금액, 마감 임박 혜택, 신청현황 및 추천 정보를 반환한다.
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;
    private final HomeActivityService homeActivityService;

    public HomeController(
            HomeService homeService,
            HomeActivityService homeActivityService
    ) {
        this.homeService = homeService;
        this.homeActivityService = homeActivityService;
    }

    /**
     * 홈 대시보드 요약 조회
     *
     * GET /api/home/summary
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/summary")
    public ResponseEntity<HomeSummaryResponse> getSummary(
            Authentication authentication
    ) {
        long userId = Long.parseLong(authentication.getName());

        HomeSummaryResponse response = homeService.getSummary(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * HOME-02 신규 해당 배지 및 주간 요약 조회.
     *
     * @param authentication 인증된 사용자 정보
     * @return 신규 해당 혜택 및 주간 요약
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/activity")
    public HomeActivityResponse getActivity(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        return homeActivityService.getActivity(userId);
    }

    /**
     * HOME-02 신규 해당 혜택 확인 처리.
     *
     * @param benefitId 혜택 ID
     * @param authentication 인증된 사용자 정보
     * @return 확인 처리 결과
     */
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/activity/benefits/{benefitId}/seen")
    public Map<String, Boolean> markBenefitAsSeen(
            @PathVariable Long benefitId,
            Authentication authentication
    ) {
        long userId = Long.parseLong(authentication.getName());

        boolean updated = homeActivityService.markBenefitAsSeen(
                userId,
                benefitId
        );

        return Map.of("updated", updated);
    }
}