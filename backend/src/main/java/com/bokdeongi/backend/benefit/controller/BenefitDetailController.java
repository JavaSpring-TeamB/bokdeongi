package com.bokdeongi.backend.benefit.controller;

import com.bokdeongi.backend.benefit.dto.response.BenefitDetailResponse;
import com.bokdeongi.backend.benefit.service.BenefitDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혜택 상세")
@RestController
@RequestMapping("/api/benefits")
public class BenefitDetailController {

    private final BenefitDetailService benefitDetailService;

    public BenefitDetailController(
            BenefitDetailService benefitDetailService
    ) {
        this.benefitDetailService = benefitDetailService;
    }

    @Operation(summary = "혜택 상세 조회")
    @GetMapping("/{benefitId}")
    public ResponseEntity<BenefitDetailResponse> getDetail(
            @PathVariable Long benefitId
    ) {
        return ResponseEntity.ok(
                benefitDetailService.getDetail(benefitId)
        );
    }
}