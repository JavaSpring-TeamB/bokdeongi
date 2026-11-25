package com.bokdeongi.backend.benefit.controller;

import com.bokdeongi.backend.benefit.dto.response.EasyResponse;
import com.bokdeongi.backend.benefit.easy.EasyOnView;
import com.bokdeongi.backend.benefit.entity.Benefit;
import com.bokdeongi.backend.benefit.repository.BenefitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "혜택 상세")
@RestController
@RequestMapping("/api/benefits")
public class BenefitEasyController {

    private final BenefitRepository benefitRepository;
    private final EasyOnView easyOnView;

    public BenefitEasyController(BenefitRepository benefitRepository, EasyOnView easyOnView) {
        this.benefitRepository = benefitRepository;
        this.easyOnView = easyOnView;
    }

    @Operation(summary = "혜택 상세 - 쉬운 말 조회",
            description = "저장된 쉬운 말이 있으면 바로 준다. 없으면 로그인한 사람이 처음 볼 때 한 번 만들어 저장한다(몇 초 걸림). "
                    + "status: DONE(글 있음) / FAILED(검사 탈락, 원문만 표시) / NOT_READY(AI 꺼짐·하루 한도·로그인 안 함, 원문만 표시)")
    @GetMapping("/{benefitId}/easy")
    public ResponseEntity<EasyResponse> getEasy(
            @PathVariable Long benefitId,
            Authentication authentication
    ) {
        Benefit benefit = benefitRepository.findById(benefitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 혜택입니다."
                ));
        boolean loggedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return ResponseEntity.ok(easyOnView.view(benefit, loggedIn));
    }
}
