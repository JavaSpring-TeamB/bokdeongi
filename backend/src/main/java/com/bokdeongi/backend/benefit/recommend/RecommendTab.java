package com.bokdeongi.backend.benefit.recommend;

import java.util.Locale;

/** 맞춤 혜택 3탭 (D-71, BEN-01·BEN-05). */
public enum RecommendTab {
    /** 해당 */
    ELIGIBLE("e.result = 'ELIGIBLE'"),
    /** 조건 확인 필요 */
    CONDITIONAL("e.result = 'CONDITIONAL'"),
    /** 아깝게 탈락: 해당 없음 중 시간이 지나면 충족되는 조건 하나만 미달 (D-80) */
    NEAR_MISS("e.result = 'INELIGIBLE' AND e.near_miss = TRUE");

    /** eligibility_results e 기준 WHERE 조각 (고정 문자열이라 SQL 주입 없음) */
    final String where;

    RecommendTab(String where) {
        this.where = where;
    }

    /** "eligible", "near-miss", "NEAR_MISS" 모두 받는다 (화면 값과 명세 값) */
    public static RecommendTab parse(String value) {
        if (value == null || value.isBlank()) {
            return ELIGIBLE;
        }
        try {
            return valueOf(value.strip().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RecommendException("tab은 ELIGIBLE / CONDITIONAL / NEAR_MISS 중 하나입니다: " + value);
        }
    }
}
