package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.favorite.FavoriteController;
import com.bokdeongi.backend.common.exception.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 맞춤 목록·추가 질문·관심 목록 API의 값 오류 → 400 BENEFIT_002 (공통 형식 ApiErrorResponse) */
@RestControllerAdvice(assignableTypes = {RecommendController.class, AnswerController.class, FavoriteController.class})
public class RecommendExceptionHandler {

    @ExceptionHandler(RecommendException.class)
    public ResponseEntity<ApiErrorResponse> handle(RecommendException e) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("BENEFIT_002", e.getMessage()));
    }
}
