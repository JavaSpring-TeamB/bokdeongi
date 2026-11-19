package com.bokdeongi.backend.home.controller;

import com.bokdeongi.backend.home.dto.response.HomeSummaryResponse;
import com.bokdeongi.backend.home.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * HOME-01 홈 대시보드 API Controller
 *
 * 로그인 사용자의 홈 대시보드 요약 정보를 조회하는 API를 제공한다.
 * 사용자 ID를 인증 정보에서 추출하고 HomeService를 호출하여
 * 프로필, 수령 가능 금액, 마감 임박 혜택, 신청현황 및 추천 정보를 반환한다.
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
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
}