package com.bokdeongi.backend.benefit.favorite;

import com.bokdeongi.backend.benefit.recommend.RecommendController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 관심 혜택 탭 (BEN-12). 등록·해제(POST·DELETE)는 BEN-07(A) */
@Tag(name = "관심 혜택")
@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @Operation(summary = "관심 혜택 목록", description = "sort = DEADLINE(마감순, 기본) / SAVED(저장순). 판정 배지·D-day·해시태그, "
            + "신청 중 배지, 공고 변경 요약(DATA-08), 중복 수혜 경고(BEN-09). 종료·만료 혜택은 ended에 따로")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<FavoriteResponse> favorites(Authentication authentication,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(service.favorites(RecommendController.userId(authentication), sort));
    }
}
