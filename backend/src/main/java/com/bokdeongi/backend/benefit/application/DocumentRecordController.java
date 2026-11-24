package com.bokdeongi.backend.benefit.application;

import com.bokdeongi.backend.benefit.recommend.RecommendController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * 서류 유효기간 기억 API
 */
@RestController
@RequestMapping("/api/me/document-records")
@SecurityRequirement(name = "bearerAuth")
public class DocumentRecordController {

    private final DocumentRecordService service;

    public DocumentRecordController(
            DocumentRecordService service
    ) {
        this.service = service;
    }

    // 로그인한 사용자의 서류 기록 및 유효기간 조회
    @GetMapping
    @Operation(summary = "기억한 서류 및 유효기간 조회")
    public DocumentRecordResponse getRecords(
            @RequestParam(required = false) Long documentTypeId,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        return service.getDocumentRecords(
                userId,
                documentTypeId
        );
    }

    // 로그인한 사용자의 서류 발급일 등록
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "서류 발급일 기억")
    public void registerRecord(
            @Valid @RequestBody DocumentRecordRequest request,
            Authentication authentication
    ) {
        long userId = RecommendController.userId(authentication);

        service.registerDocumentRecord(userId, request);
    }

    /**
     * 서류 기록 API 전용 예외 처리
     */
    @ExceptionHandler(DocumentRecordException.class)
    public ResponseEntity<Map<String, String>> handleDocumentRecordException(
            DocumentRecordException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage()
                ));
    }
}