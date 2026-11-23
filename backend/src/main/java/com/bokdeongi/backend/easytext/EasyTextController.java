package com.bokdeongi.backend.easytext;

import com.bokdeongi.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쉬운 말 번역기 (EASY-02). 로그인 없이 쓸 수 있다 (하루 20번) */
@Tag(name = "쉬운 말 번역기")
@RestController
@RequestMapping("/api/easy-text")
public class EasyTextController {

    private final EasyTranslatorService service;

    public EasyTextController(EasyTranslatorService service) {
        this.service = service;
    }

    @Operation(summary = "안내문 쉬운 말 변환",
            description = "고지서·안내문 글(5,000자 이내)을 쉬운 말 묶음으로 바꾼다. 주민번호·휴대전화·이메일은 가리고 AI에 보내며 "
                    + "글과 결과는 저장하지 않는다. 묶음의 original은 원문에 그대로 있는 문장만. 로그인하면 회원별, 아니면 접속 주소별 하루 20번")
    @PostMapping
    public ResponseEntity<EasyTextDto.Response> translate(Authentication authentication, HttpServletRequest request,
                                                          @RequestBody EasyTextDto.Request body) {
        return ResponseEntity.ok(service.translate(clientKey(authentication, request), body == null ? null : body.text()));
    }

    @ExceptionHandler(EasyTextException.class)
    public ResponseEntity<ApiErrorResponse> handle(EasyTextException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    /** 로그인하면 회원 번호, 아니면 접속 주소 */
    static String clientKey(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String name = authentication.getName();
            if (name != null && name.chars().allMatch(Character::isDigit) && !name.isEmpty()) {
                return "user:" + name;
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
