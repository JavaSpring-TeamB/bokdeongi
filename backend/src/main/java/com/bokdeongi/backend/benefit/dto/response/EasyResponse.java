package com.bokdeongi.backend.benefit.dto.response;

import java.time.LocalDateTime;

/**
 * 공고문 쉬운 말 (EASY-01, BN-07 easy=true 의 easy·summary).
 *
 * @param status      DONE / FAILED. FAILED면 프론트는 원문만 보여 준다
 * @param text        쉬운 말 전체 글. FAILED면 null
 * @param summary     3줄 요약 (누가·무엇을·어떻게·언제). FAILED면 null
 * @param generatedAt 만든 시각
 * @param notice      화면에 늘 붙이는 안내 문구
 */
public record EasyResponse(String status, String text, Summary summary, LocalDateTime generatedAt, String notice) {

    public static final String NOTICE = "AI가 바꾼 문장이에요. 정확한 내용은 원문을 확인하세요";

    public record Summary(String who, String what, String how, String when) {
    }

    public static EasyResponse failed() {
        return new EasyResponse("FAILED", null, null, null, NOTICE);
    }

    /** 아직 만든 글이 없음 (AI 꺼짐·하루 한도). 프론트는 원문만 보여 준다 */
    public static EasyResponse notReady() {
        return new EasyResponse("NOT_READY", null, null, null, NOTICE);
    }
}
