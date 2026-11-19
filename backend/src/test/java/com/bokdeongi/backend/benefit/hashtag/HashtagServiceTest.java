package com.bokdeongi.backend.benefit.hashtag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class HashtagServiceTest {

    @Test
    void 사전_순서대로_태그별_혜택_수와_태그_없는_수() {
        List<String[]> dictionary = List.of(
                new String[] {"월세", "분야", "월세 지원"},
                new String[] {"청년", "대상", null},
                new String[] {"노인", "대상", null});
        HashtagResponse r = HashtagService.count(dictionary, Arrays.asList(
                "[\"월세\",\"청년\"]", "[\"청년\",\"청년\"]", "[]", null, "깨진 값", "[\"사전에없음\"]"));
        assertEquals(List.of("월세", "청년", "노인"), r.tags().stream().map(HashtagResponse.Tag::name).toList());
        assertEquals(List.of(1L, 2L, 0L), r.tags().stream().map(HashtagResponse.Tag::count).toList());
        assertEquals(3, r.untaggedCount());
    }
}
