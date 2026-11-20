package com.bokdeongi.backend.search.controller;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.dto.request.BenefitListRequest;
import com.bokdeongi.backend.search.dto.response.BenefitSearchResponse;
import com.bokdeongi.backend.search.service.BenefitListService;
import com.bokdeongi.backend.search.service.BenefitSearchEligibilityService;
import com.bokdeongi.backend.search.service.BenefitSearchPeriodService;
import com.bokdeongi.backend.search.service.BenefitSearchSuggestionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/benefits")
public class BenefitListController {

    private final BenefitListService benefitListService;
    private final BenefitSearchEligibilityService eligibilityService;
    private final BenefitSearchSuggestionService suggestionService;
    private final BenefitSearchPeriodService periodService;

    public BenefitListController(
            BenefitListService benefitListService,
            BenefitSearchEligibilityService eligibilityService,
            BenefitSearchSuggestionService suggestionService,
            BenefitSearchPeriodService periodService
    ) {
        this.benefitListService = benefitListService;
        this.eligibilityService = eligibilityService;
        this.suggestionService = suggestionService;
        this.periodService = periodService;
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<BenefitSearchResponse> findBenefits(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String hashtag,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        BenefitListRequest request = new BenefitListRequest(
                q, hashtag, sort, page, size
        );

        Page<Benefit> result = benefitListService.findBenefits(request);

        Long userId = null;

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long id) {
            userId = id;
        }

        Map<Long, String> eligibilityResults =
                eligibilityService.getEligibilityResults(userId);

        List<String> suggestedHashtags = List.of();

        if (request.q() != null && result.getTotalElements() == 0) {
            suggestedHashtags = suggestionService.suggest(request.q());
        }

        BenefitSearchResponse response = BenefitSearchResponse.from(
                request.q() == null ? "" : request.q(),
                result,
                eligibilityResults,
                suggestedHashtags,
                periodService
        );

        return ResponseEntity.ok(response);
    }
}