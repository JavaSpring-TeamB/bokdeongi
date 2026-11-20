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