package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Item;
import java.util.Map;

/**
 * @param answers 저장 후 회원의 전체 답
 * @param benefit 다시 판정한 카드 (benefitId를 보냈을 때). 나머지 혜택은 뒤에서 다시 판정된다
 */
public record AnswerResponse(Map<String, String> answers, Item benefit) {
}
