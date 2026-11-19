package com.bokdeongi.backend.benefit.hashtag;

import java.util.List;

/**
 * GET /api/hashtags 응답 (BEN-10). 태그 칩·관심 키워드 선택 목록에 같이 쓴다 (D-82).
 *
 * @param tags           사전(active)의 태그, 노출 순서(sort_order)대로
 * @param untaggedCount  태그가 하나도 없는 혜택 수 ('기타')
 */
public record HashtagResponse(List<Tag> tags, long untaggedCount) {

    /** @param count 이 태그가 붙은 신청 가능 혜택 수 (종료·만료·시설 대상 제외) */
    public record Tag(String name, String category, String description, long count) {
    }
}
