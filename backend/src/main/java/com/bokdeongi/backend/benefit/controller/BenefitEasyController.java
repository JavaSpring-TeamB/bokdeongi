package com.bokdeongi.backend.benefit.controller;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Tag(name = "혜택 상세")
@RestController
@RequestMapping("/api/benefits")
public class BenefitEasyController {

    private final BenefitRepository benefitRepository;

    public BenefitEasyController(BenefitRepository benefitRepository) {
        this.benefitRepository = benefitRepository;
    }

    @Operation(summary = "혜택 상세 - 저장된 쉬운 말 조회")
    @GetMapping("/{benefitId}/easy")
    public ResponseEntity<EasyResponse> getEasy(
            @PathVariable Long benefitId
    ) {
        Benefit benefit = benefitRepository.findById(benefitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 혜택입니다."
                ));


        boolean fresh = benefit.getAiGeneratedAt() != null
                && Objects.equals(
                benefit.getAiSourceModifiedAt(),
                benefit.getApiModifiedAt()
        );

        if (!fresh) {
            return ResponseEntity.ok(new EasyResponse(
                    "NOT_READY",
                    null,
                    null,
                    null,
                    EasyResponse.NOTICE
            ));
        }

        if ("FAILED".equals(benefit.getAiStatus())) {
            return ResponseEntity.ok(EasyResponse.failed());
        }

        if (!"DONE".equals(benefit.getAiStatus())
                || benefit.getEasyText() == null
                || benefit.getEasyText().isBlank()) {
            return ResponseEntity.ok(new EasyResponse(
                    "NOT_READY",
                    null,
                    null,
                    null,
                    EasyResponse.NOTICE
            ));
        }

        EasyResponse response = new EasyResponse(
                "DONE",
                benefit.getEasyText(),
                new EasyResponse.Summary(
                        benefit.getSummaryWho(),
                        benefit.getSummaryWhat(),
                        benefit.getSummaryHow(),
                        benefit.getSummaryWhen()
                ),
                benefit.getAiGeneratedAt(),
                EasyResponse.NOTICE
        );

        return ResponseEntity.ok(response);
    }
}