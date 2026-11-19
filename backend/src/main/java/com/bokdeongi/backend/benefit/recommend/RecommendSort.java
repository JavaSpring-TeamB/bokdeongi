package com.bokdeongi.backend.benefit.recommend;

import java.util.Locale;

/**
 * 정렬 (BEN-02). 값이 없는 혜택은 맨 뒤(마감일 없음·금액 미확인). 같은 값이면 혜택 id 순으로 고정한다.
 * b = benefits
 */
public enum RecommendSort {
    /** 추천순: 연간 금액(없으면 최대 금액) 큰 순 → 마감 임박 순 */
    RECOMMENDED("(COALESCE(b.annual_amount, b.max_amount) IS NULL), COALESCE(b.annual_amount, b.max_amount) DESC, "
            + "(b.apply_end IS NULL), b.apply_end ASC, b.id"),
    /** 마감 임박순 */
    DEADLINE("(b.apply_end IS NULL), b.apply_end ASC, b.id"),
    /** 지원 금액순 */
    AMOUNT("(COALESCE(b.annual_amount, b.max_amount) IS NULL), COALESCE(b.annual_amount, b.max_amount) DESC, b.id"),
    /** 신청 쉬운 순: 온라인 신청 먼저 → 내가 낼 서류 적은 순 (BEN-03) */
    EASY("(b.online_apply IS NULL), b.online_apply DESC, (b.self_doc_count IS NULL), b.self_doc_count ASC, b.id");

    /** ORDER BY 조각 (고정 문자열) */
    final String orderBy;

    RecommendSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public static RecommendSort parse(String value) {
        if (value == null || value.isBlank()) {
            return RECOMMENDED;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RecommendException("sort는 RECOMMENDED / DEADLINE / AMOUNT / EASY 중 하나입니다: " + value);
        }
    }
}
