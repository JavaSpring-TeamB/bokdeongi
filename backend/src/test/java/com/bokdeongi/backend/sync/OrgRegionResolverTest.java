package com.bokdeongi.backend.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrgRegionResolverTest {

    private static final String T = "20261101090000";

    private static ServiceRecord rec(String type, String name, String code) {
        return SyncFixtures.record("S-" + name, "혜택", type, name, code, T);
    }

    /** 시군구·광역시도 공고로 기관코드 표를 만든다 */
    private static final List<ServiceRecord> SEED = List.of(
            rec("시군구", "서울특별시 관악구", "3200000"),
            rec("광역시도", "부산광역시", "6260000"),
            rec("시군구", "인천광역시 중구", "3490000"),
            rec("시군구", "경기도 수원시", "3740000"));

    private static final OrgRegionResolver R = new OrgRegionResolver(SEED, SyncFixtures.REGIONS);

    @Test
    void 중앙행정기관과_공공기관은_전국() {
        assertNull(R.resolve(rec("중앙행정기관", "보건복지부", "1352000")).code());
        assertNull(R.resolve(rec("공공기관", "한국장학재단", "B552134")).code());
    }

    @Test
    void 시군구는_기관명으로() {
        assertEquals("11620", R.resolve(SEED.get(0)).code());
        assertEquals("26", R.resolve(SEED.get(1)).code());
    }

    @Test
    void 지방공기업_출연기관은_같은_기관코드로() {
        OrgRegionResolver.Resolved r = R.resolve(rec("지방공기업", "관악구시설관리공단", "3200000"));
        assertEquals("11620", r.code());
        assertTrue(r.found());
    }

    @Test
    void 교육청은_이름의_시도로() {
        assertEquals("26", R.resolve(rec("교육청", "부산광역시교육청", "7150000")).code());
        assertEquals("26", R.resolve(rec("교육청", "부산광역시해운대교육지원청", "7151000")).code());
    }

    @Test
    void 옛_시도_이름은_지금_이름으로() {
        assertEquals("12300", R.resolve(rec("시군구", "광주광역시 북구", "3610000")).code());
        assertEquals("12110", R.resolve(rec("시군구", "전라남도 목포시", "4800000")).code());
    }

    @Test
    void 개편으로_없어진_구는_시도로_넓힌다() {
        OrgRegionResolver.Resolved r = R.resolve(SEED.get(2));
        assertEquals("28", r.code());
        assertTrue(r.widened());
    }

    @Test
    void 일반구가_붙은_이름은_시까지만() {
        assertEquals("41110", R.resolve(rec("시군구", "경기도 수원시장안구", "3741000")).code());
    }

    @Test
    void 세종은_시도_한_줄() {
        assertEquals("36110", R.resolve(rec("광역시도", "세종특별자치시", "5690000")).code());
    }

    @Test
    void 지자체인데_지역을_못_찾으면_기록한다() {
        OrgRegionResolver.Resolved r = R.resolve(rec("지방출자_출연기관", "재단법인어딘가재단", "9999999"));
        assertNull(r.code());
        assertFalse(r.found());
    }

    @Test
    void 줄임말은_띄어쓰기가_있을_때만() {
        assertEquals("부산광역시", OrgRegionResolver.place("부산 시설공단")[0]);
        assertNull(OrgRegionResolver.place("부산진구시설관리공단"));
    }
}
