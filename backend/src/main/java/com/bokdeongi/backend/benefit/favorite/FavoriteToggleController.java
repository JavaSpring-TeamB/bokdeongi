package com.bokdeongi.backend.benefit.favorite;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.bokdeongi.backend.benefit.recommend.RecommendController;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 혜택 관심 저장 관리 API 컨트롤러.
 *
 * 로그인한 회원의 관심 혜택 등록, 해제 및
 * 현재 관심 저장 여부 조회 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/benefits/{benefitId}/favorite")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteToggleController {

    private final FavoriteToggleService service;

    public FavoriteToggleController(FavoriteToggleService service) {
        this.service = service;
    }

    // 관심 등록
    @PostMapping
    public FavoriteToggleResponse add(
            @PathVariable long benefitId,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);
        boolean bookmarked = service.add(userId, benefitId);

        return new FavoriteToggleResponse(benefitId, bookmarked);
    }

    // 관심 해제
    @DeleteMapping
    public FavoriteToggleResponse remove(
            @PathVariable long benefitId,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);
        boolean bookmarked = service.remove(userId, benefitId);

        return new FavoriteToggleResponse(benefitId, bookmarked);
    }

    // 관심 여부 조회
    @GetMapping
    public FavoriteToggleResponse get(
            @PathVariable long benefitId,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);
        boolean bookmarked = service.isBookmarked(userId, benefitId);

        return new FavoriteToggleResponse(benefitId, bookmarked);
    }
}