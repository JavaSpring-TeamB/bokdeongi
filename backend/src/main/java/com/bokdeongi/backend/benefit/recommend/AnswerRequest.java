package com.bokdeongi.backend.benefit.recommend;

import java.util.Map;

/**
 * PUT /api/me/answers 요청 (BEN-04, PROF-06 저장).
 *
 * @param answers   질문 키 → Y / N / UNSURE(아직 몰라요, D-90). 예: {"doctor_says_complication_test": "UNSURE"}
 * @param benefitId 질문에 답한 카드의 혜택 id. 있으면 그 카드만 바로 다시 판정해 응답에 담는다 (선택)
 */
public record AnswerRequest(Map<String, String> answers, Long benefitId) {
}
