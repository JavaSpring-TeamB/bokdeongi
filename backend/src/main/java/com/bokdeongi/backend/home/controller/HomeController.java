package com.bokdeongi.backend.home.controller;

import com.bokdeongi.backend.home.dto.response.HomeSummaryResponse;
import com.bokdeongi.backend.home.dto.response.HomeActivityResponse;
import com.bokdeongi.backend.home.dto.response.HomeCalendarResponse;
import com.bokdeongi.backend.home.service.HomeCalendarService;
import com.bokdeongi.backend.home.service.HomeActivityService;
import com.bokdeongi.backend.home.service.HomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

/**
 * HOME-01, HOME-02, HOME-03 홈 관련 API 컨트롤러.
 *
 * 로그인 사용자의 인증 정보에서 회원 ID를 추출하여
 * 홈 대시보드, 신규 해당 혜택, 주간 요약 및
 * 신청 일정 캘린더 정보를 반환한다.
 *
 * 신규 해당 혜택의 확인 처리 요청도 담당한다.
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;
    private final HomeActivityService homeActivityService;
    private final HomeCalendarService homeCalendarService;

    public HomeController(
            HomeService homeService,
            HomeActivityService homeActivityService,
            HomeCalendarService homeCalendarService
    ) {
        this.homeService = homeService;
        this.homeActivityService = homeActivityService;
        this.homeCalendarService = homeCalendarService;
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

    /**
     * HOME-03 홈 신청 일정 캘린더 조회.
     *
     * @param year 조회 연도
     * @param month 조회 월 (1~12)
     * @param authentication 인증된 사용자 정보
     * @return 월별 신청 일정
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/calendar")
    public HomeCalendarResponse getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            Authentication authentication
    ) {
        long userId = Long.parseLong(authentication.getName());

        return homeCalendarService.getCalendar(
                userId,
                year,
                month
        );
    }
}