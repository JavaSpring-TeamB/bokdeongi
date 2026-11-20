package com.bokdeongi.backend.search.dto.response;

import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.search.service.BenefitSearchPeriodService;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public record BenefitSearchResponse(
        String keyword,
        List<BenefitSearchItem> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext,
        List<String> suggestedHashtags
) {

    public static BenefitSearchResponse from(
            String keyword,
            Page<Benefit> result
    ) {
        return from(keyword, result, Map.of(), List.of(), null);
    }

    public static BenefitSearchResponse from(
            String keyword,
            Page<Benefit> result,
            Map<Long, String> eligibilityResults
    ) {
        return from(keyword, result, eligibilityResults, List.of(), null);
    }

    public static BenefitSearchResponse from(
            String keyword,
            Page<Benefit> result,
            Map<Long, String> eligibilityResults,
            List<String> suggestedHashtags
    ) {
        return from(
                keyword,
                result,
                eligibilityResults,
                suggestedHashtags,
                null
        );
    }

    public static BenefitSearchResponse from(
            String keyword,
            Page<Benefit> result,
            Map<Long, String> eligibilityResults,
            List<String> suggestedHashtags,
            BenefitSearchPeriodService periodService
    ) {
        List<BenefitSearchItem> items = result.getContent()
                .stream()
                .map(benefit -> BenefitSearchItem.from(
                        benefit,
                        eligibilityResults.get(benefit.getId()),
                        periodService == null
                                ? null
                                : periodService.calculate(benefit)
                ))
                .toList();

        return new BenefitSearchResponse(
                keyword,
                items,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.hasNext(),
                suggestedHashtags
        );
    }
}