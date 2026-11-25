package com.bokdeongi.backend.search.controller;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.dto.response.BenefitSearchResponse;
import com.bokdeongi.backend.search.service.BenefitSearchEligibilityService;
import com.bokdeongi.backend.search.service.BenefitSearchPeriodService;
import com.bokdeongi.backend.search.service.BenefitSearchService;
import com.bokdeongi.backend.search.service.BenefitSearchSuggestionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * BEN-11: 혜택 키워드 검색 API 컨트롤러.
 *
 * 검색어와 페이지 정보를 전달받아 검색 결과를 조회하고,
 * 사용자별 자격 판정 및 추천 해시태그 정보를 포함하여 반환한다.
 */
@RestController
@RequestMapping("/api/benefits")
public class BenefitSearchController {

    private final BenefitSearchService benefitSearchService;
    private final BenefitSearchEligibilityService eligibilityService;
    private final BenefitSearchSuggestionService suggestionService;
    private final BenefitSearchPeriodService periodService;

    public BenefitSearchController(
            BenefitSearchService benefitSearchService,
            BenefitSearchEligibilityService eligibilityService,
            BenefitSearchSuggestionService suggestionService,
            BenefitSearchPeriodService periodService
    ) {
        this.benefitSearchService = benefitSearchService;
        this.eligibilityService = eligibilityService;
        this.suggestionService = suggestionService;
        this.periodService = periodService;
    }

    /**
     * 키워드에 해당하는 혜택을 페이지 단위로 조회한다.
     * 검색 결과가 없으면 관련 해시태그를 제공한다.
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/search")
    public ResponseEntity<BenefitSearchResponse> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        Page<Benefit> result = benefitSearchService.search(
                keyword, page, size
        );

        Long userId = null;

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long id) {
            userId = id;
        }

        Map<Long, String> eligibilityResults =
                eligibilityService.getEligibilityResults(userId);

        List<String> suggestedHashtags = List.of();

        if (result.getTotalElements() == 0) {
            suggestedHashtags = suggestionService.suggest(keyword);
        }

        return ResponseEntity.ok(
                BenefitSearchResponse.from(
                        keyword.trim(),
                        result,
                        eligibilityResults,
                        suggestedHashtags,
                        periodService
                )
        );
    }
}