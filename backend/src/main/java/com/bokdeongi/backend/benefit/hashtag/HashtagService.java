package com.bokdeongi.backend.benefit.hashtag;

import com.bokdeongi.backend.eligibility.store.JsonText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 해시태그 목록과 태그별 혜택 수 (BEN-10).
 *
 * <p>태그별 수는 저장하지 않고 계산한다 (hashtags 정의서). 혜택 약 1만 건의 hashtags(JSON 배열)를 한 번 읽어 세는 쪽이
 * 태그마다 JSON_CONTAINS로 전체를 훑는 것(40번)보다 싸다. 태그별 혜택 목록은 맞춤 목록의 hashtag 필터
 * (GET /api/benefits/recommended?hashtag=)와 전체 목록(GET /api/benefits?hashtag=, A)이 맡는다.
 */
@Service
@Transactional(readOnly = true)
public class HashtagService {

    /** 개인에게 보여 줄 수 있는 혜택: 신청 가능하고 시설·기관 전용이 아님 */
    static final String LISTED = "status = 'ACTIVE' AND (target_scope IS NULL OR target_scope <> 'CORP')";

    private final JdbcTemplate jdbc;

    public HashtagService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HashtagResponse hashtags() {
        List<String[]> dictionary = jdbc.query(
                "SELECT name, category, description FROM hashtags WHERE active = TRUE ORDER BY sort_order, id",
                (rs, n) -> new String[] {rs.getString("name"), rs.getString("category"), rs.getString("description")});
        List<String> tagJson = jdbc.query("SELECT hashtags FROM benefits WHERE " + LISTED,
                (rs, n) -> rs.getString("hashtags"));
        return count(dictionary, tagJson);
    }

    /** 사전 순서대로 태그별 혜택 수와 태그 없는 혜택 수 */
    static HashtagResponse count(List<String[]> dictionary, List<String> tagJson) {
        Map<String, Long> counts = new HashMap<>();
        long untagged = 0;
        for (String json : tagJson) {
            List<String> tags;
            try {
                tags = JsonText.stringList(json);
            } catch (IllegalArgumentException e) {
                tags = null;
            }
            if (tags == null || tags.isEmpty()) {
                untagged++;
                continue;
            }
            for (String t : tags.stream().distinct().toList()) {
                counts.merge(t, 1L, Long::sum);
            }
        }
        List<HashtagResponse.Tag> out = new ArrayList<>();
        for (String[] d : dictionary) {
            out.add(new HashtagResponse.Tag(d[0], d[1], d[2], counts.getOrDefault(d[0], 0L)));
        }
        return new HashtagResponse(out, untagged);
    }
}
