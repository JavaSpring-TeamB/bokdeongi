package com.bokdeongi.backend.easytext;

import com.bokdeongi.backend.common.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 쉬운 말 번역기 (EASY-02). 로그인해야 쓸 수 있다 (회원별 하루 20번, 봇이 한도를 다 쓰거나 OCR 서버에 부하를 주지 않게) */
@Tag(name = "쉬운 말 번역기")
@RestController
@RequestMapping("/api/easy-text")
public class EasyTextController {

    private final EasyTranslatorService service;

    public EasyTextController(EasyTranslatorService service) {
        this.service = service;
    }

    @Operation(summary = "안내문 쉬운 말 변환",
            description = "고지서·안내문 글(5,000자 이내)을 쉬운 말 묶음으로 바꾼다. 주민번호·휴대전화·이메일·계좌·상세주소는 가리고 AI에 보내며 "
                    + "글과 결과는 저장하지 않는다. 묶음의 original은 원문에 그대로 있는 문장만. 로그인 필요(401), 회원별 하루 20번. AI 호출이 실패하면 횟수를 쓰지 않는다")
    @PostMapping
    public ResponseEntity<EasyTextDto.Response> translate(Authentication authentication, HttpServletRequest request,
                                                          @RequestBody EasyTextDto.Request body) {
        return ResponseEntity.ok(service.translate(clientKey(authentication, request), body == null ? null : body.text()));
    }

    @Operation(summary = "고지서 사진·PDF 쉬운 말 변환",
            description = "multipart: file(JPG·PNG·PDF, 10MB). 로컬 OCR 서버가 글자를 읽고 주민번호·전화·계좌·상세주소를 가린 뒤 "
                    + "글 변환과 같은 길로 보낸다. 파일은 저장하지 않는다. 응답은 POST /api/easy-text와 같다. "
                    + "로그인 필요(401). 흐린 사진 422 UNREADABLE, 등본·납부확인서 같은 개인 서류 422 PERSONAL_DOCUMENT(AI에 안 보냄), 형식 415, 크기 413, OCR 서버 꺼짐 502")
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EasyTextDto.Response> translateFile(Authentication authentication, HttpServletRequest request,
                                                              @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.translateFile(clientKey(authentication, request), file));
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
