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

/**
 * 혜택 상세 조회 API 컨트롤러.
 *
 * 혜택 ID를 전달받아 지원 대상, 지원 내용,
 * 신청 기간, 신청 방법 및 필요 서류 등의 정보를 반환한다.
 */
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

    /**
     * 혜택 ID를 기준으로 상세 정보를 조회한다.
     * 존재하지 않는 혜택은 404 오류로 처리한다.
     */
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