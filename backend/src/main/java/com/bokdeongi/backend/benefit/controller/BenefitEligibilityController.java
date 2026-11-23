package com.bokdeongi.backend.benefit.controller;

import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse;
import com.bokdeongi.backend.benefit.assist.EligibilityCheckService;
import com.bokdeongi.backend.benefit.recommend.RecommendController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "혜택 상세")
@RestController
@RequestMapping("/api/benefits")
public class BenefitEligibilityController {

    private final EligibilityCheckService eligibilityCheckService;

    public BenefitEligibilityController(
            EligibilityCheckService eligibilityCheckService
    ) {
        this.eligibilityCheckService = eligibilityCheckService;
    }

    @Operation(summary = "혜택 상세 - 내 자격 판정 및 조건별 근거")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{benefitId}/eligibility")
    public ResponseEntity<EligibilityCheckResponse> getEligibility(
            @PathVariable Long benefitId,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        EligibilityCheckResponse response = eligibilityCheckService
                .check(userId, benefitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "판정 가능한 혜택을 찾을 수 없습니다."
                ));

        return ResponseEntity.ok(response);
    }
}