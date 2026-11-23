package com.bokdeongi.backend.search.controller;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.dto.request.BenefitListRequest;
import com.bokdeongi.backend.search.dto.response.BenefitSearchResponse;
import com.bokdeongi.backend.search.service.BenefitListService;
import com.bokdeongi.backend.search.service.BenefitSearchEligibilityService;
import com.bokdeongi.backend.search.service.BenefitSearchPeriodService;
import com.bokdeongi.backend.search.service.BenefitSearchSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "혜택 목록·필터 (BEN-11)", description = "status = OPEN(신청 가능) / UPCOMING(신청 예정) / "
            + "CLOSED(신청 마감), 여러 개는 쉼표나 반복(status=OPEN&status=UPCOMING). 비우면 OPEN·UPCOMING. "
            + "field = 분야 해시태그, target = 대상 해시태그 (GET /api/hashtags의 category 분야·대상 이름). "
            + "같은 칸 안은 '또는', 칸끼리는 '그리고'. sort = relevance(관련도, 기본) / deadline(마감 임박) / "
            + "amount(지원 금액) / easy(신청 쉬운 순). 값이 없는 혜택은 맨 뒤")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<BenefitSearchResponse> findBenefits(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String hashtag,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> field,
            @RequestParam(required = false) List<String> target,
            Authentication authentication
    ) {
        BenefitListRequest request = new BenefitListRequest(
                q, hashtag, sort, page, size,
                status == null ? null : new java.util.LinkedHashSet<>(status), field, target, null
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