package com.bokdeongi.backend.search.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.search.dto.request.BenefitListRequest;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BenefitListRequestTest {

    private static BenefitListRequest req(Set<String> status, List<String> field, List<String> target) {
        return new BenefitListRequest(null, null, null, 0, 10, status, field, target, LocalDate.of(2026, 11, 23));
    }

    @Test
    void 상태를_고르지_않으면_가능과_예정만() {
        BenefitListRequest r = new BenefitListRequest(null, null, null, 0, 10);
        assertTrue(r.statusOpen());
        assertTrue(r.statusUpcoming());
        assertFalse(r.statusClosed());
        assertNull(r.fieldsJson());
        assertNull(r.targetsJson());
    }

    @Test
    void 상태는_대소문자와_ALL을_받고_모르는_값은_거절() {
        BenefitListRequest r = req(Set.of("closed", " open "), null, null);
        assertTrue(r.statusOpen());
        assertTrue(r.statusClosed());
        assertFalse(r.statusUpcoming());
        assertTrue(req(Set.of("ALL"), null, null).statusOpen());   // 전체 = 기본
        assertThrows(IllegalArgumentException.class, () -> req(Set.of("DONE"), null, null));
    }

    @Test
    void 분야와_대상은_중복을_빼고_JSON_배열로() {
        BenefitListRequest r = req(null, List.of("주거", " 주거 ", "의료"), List.of("청년"));
        assertEquals("[\"주거\",\"의료\"]", r.fieldsJson());
        assertEquals("[\"청년\"]", r.targetsJson());
        assertEquals("[\"a\\\"b\"]", req(null, List.of("a\"b"), null).fieldsJson());
    }

    @Test
    void 필터_개수와_길이_제한() {
        assertThrows(IllegalArgumentException.class,
                () -> req(null, Collections.nCopies(1, "가".repeat(31)), null));
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add("태그" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> req(null, null, many));
    }

    @Test
    void 정렬은_네_가지만_받고_대소문자와_빈값을_정리() {
        assertEquals("relevance", new BenefitListRequest(null, null, null, 0, 10).sort());
        assertEquals("relevance", new BenefitListRequest(null, null, " ", 0, 10).sort());
        assertEquals("deadline", new BenefitListRequest(null, null, "DEADLINE", 0, 10).sort());
        assertEquals("amount", new BenefitListRequest(null, null, " amount ", 0, 10).sort());
        assertEquals("easy", new BenefitListRequest(null, null, "Easy", 0, 10).sort());
        assertThrows(IllegalArgumentException.class, () -> new BenefitListRequest(null, null, "latest", 0, 10));
    }
}
