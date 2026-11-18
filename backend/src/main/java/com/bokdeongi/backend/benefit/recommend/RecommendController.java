package com.bokdeongi.backend.benefit.recommend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 맞춤 혜택 목록 (BEN-01). 예: GET /api/benefits/recommended?tab=CONDITIONAL&region=서울특별시&sort=DEADLINE&page=0&size=5
 */
@RestController
@RequestMapping("/api/benefits")
public class RecommendController {

    private final RecommendService service;

    public RecommendController(RecommendService service) {
        this.service = service;
    }

    @Operation(summary = "맞춤 혜택 목록", description = "tab = ELIGIBLE / CONDITIONAL / NEAR_MISS, sort = RECOMMENDED / "
            + "DEADLINE / AMOUNT / EASY, region = 시·도 이름 또는 전국, field = 서비스 분야")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/recommended")
    public ResponseEntity<RecommendResponse> recommended(Authentication authentication,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(service.recommended(userId(authentication), tab, region, field, sort, page, size));
    }

    static long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }
}
