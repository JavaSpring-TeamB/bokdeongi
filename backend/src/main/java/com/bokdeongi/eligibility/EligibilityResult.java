package com.bokdeongi.eligibility;

import java.util.List;

/**
 * 판정 결과.
 *
 * @param verdict       최종 3값
 * @param trace         조건별 근거 (규칙 순서 그대로)
 * @param missingFields 회원이 건너뛰어 조건부가 된 필드 = 추가 질문 대상 (BEN-04)
 */
public record EligibilityResult(Verdict verdict, List<ConditionTrace> trace, List<String> missingFields) {

    public EligibilityResult {
        trace = List.copyOf(trace);
        missingFields = List.copyOf(missingFields);
    }

    /** 미충족 조건 수. 정확히 1개면 "아깝게 탈락" 후보 (BEN-05). */
    public long failedCount() {
        return trace.stream().filter(t -> t.outcome() == Outcome.FAIL).count();
    }
}
