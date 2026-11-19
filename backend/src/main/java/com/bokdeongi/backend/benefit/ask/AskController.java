package com.bokdeongi.backend.benefit.ask;

import com.bokdeongi.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 공고문에 질문하기 (BEN-08, BN-08). 로그인 필요 (1인 하루 30회) */
@RestController
@RequestMapping("/api/benefits")
public class AskController {

    private final AskService service;

    public AskController(AskService service) {
        this.service = service;
    }

    @Operation(summary = "공고문에 질문하기",
            description = "이 혜택의 공고 원문만 보고 답한다. 근거 문장(sourceQuotes)은 원문에 그대로 있는 것만. "
                    + "자격 결론은 내지 않고 eligibility=true면 판정 결과로 연결. 1인 하루 30회")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/ask")
    public ResponseEntity<AskDto.Response> ask(Authentication authentication, @PathVariable("id") long id,
                                               @RequestBody AskDto.Request request) {
        return ResponseEntity.ok(service.ask(userId(authentication), id, request == null ? null : request.question()));
    }

    @ExceptionHandler(AskException.class)
    public ResponseEntity<ApiErrorResponse> handle(AskException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    static long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }
}
