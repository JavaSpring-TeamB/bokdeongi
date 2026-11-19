package com.bokdeongi.backend.benefit.hashtag;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 해시태그 목록 (BEN-10). 자주 쓰는 태그 칩, 관심 키워드 선택 목록(D-82)에 같이 쓴다 */
@Tag(name = "해시태그")
@RestController
@RequestMapping("/api/hashtags")
public class HashtagController {

    private final HashtagService service;

    public HashtagController(HashtagService service) {
        this.service = service;
    }

    @Operation(summary = "해시태그 목록", description = "사전(active)의 태그를 노출 순서대로, 태그별 신청 가능 혜택 수와 "
            + "태그 없는 혜택 수(기타). 태그별 혜택은 GET /api/benefits/recommended?hashtag= 로 거른다")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<HashtagResponse> hashtags() {
        return ResponseEntity.ok(service.hashtags());
    }
}
