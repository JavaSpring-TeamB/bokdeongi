package com.bokdeongi.backend.benefit.recommend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공고별 추가 질문 답 저장 (BEN-04, PROF-06). 예: {"answers": {"no_hbv_antibody": "Y"}, "benefitId": 12} */
@RestController
@RequestMapping("/api/me")
public class AnswerController {

    private final AnswerService service;

    public AnswerController(AnswerService service) {
        this.service = service;
    }

    @Operation(summary = "추가 질문 답 저장", description = "답: Y / N / UNSURE(아직 몰라요). benefitId를 보내면 그 카드를 바로 다시 판정해 돌려준다")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/answers")
    public ResponseEntity<AnswerResponse> save(Authentication authentication, @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(service.save(RecommendController.userId(authentication), request));
    }
}
