package com.bokdeongi.backend.benefit.recommend;

/** 탭·정렬·필터·답 값이 잘못됨 → 400 BENEFIT_002 (API 명세 에러 코드표) */
public class RecommendException extends RuntimeException {

    public RecommendException(String message) {
        super(message);
    }
}
