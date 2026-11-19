package com.bokdeongi.backend.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.MasterDoc;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.store.JsonText;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BenefitMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final List<MasterDoc> DOCS = List.of(
            new MasterDoc(1L, "주민등록등본", List.of("등본"), "읍·면·동 행정복지센터", true, "https://www.gov.kr", "정부24",
                    90, null, true),
            new MasterDoc(2L, "통장사본", List.of("통장 사본"), "본인 거래 은행", true, null, "은행 앱", null, null, false),
            new MasterDoc(3L, "가족관계증명서", List.of(), "대법원", true, "https://efamily.scourt.go.kr", "", 90, null,
                    false));

    private static BenefitMapper mapper(Set<String> tags) {
        ServiceRecord seed = SyncFixtures.record("S0", "혜택", "시군구", "서울특별시 관악구", "3200000", "20261101090000");
        return new BenefitMapper(new OrgRegionResolver(List.of(seed), SyncFixtures.REGIONS), DOCS, tags, TODAY);
    }

    private static ServiceRecord youthRent() {
        ServiceRecord r = SyncFixtures.record("320000000501", "관악구 청년 월세 지원", "시군구", "서울특별시 관악구",
                "3200000", "20261101090000");
        r.detail().put("신청기한유형", "기간설정");
        r.detail().put("신청기한", "2026.11.1.~2026.12.10.");
        return r;
    }

    @Test
    void 원문_칸과_수정일시() {
        BenefitRow row = mapper(Set.of()).map(youthRent());
        assertEquals("320000000501", row.serviceId());
        assertEquals("관악구 청년 월세 지원", row.title());
        assertEquals("관악구 청년 월세 지원 요약", row.purposeText());
        assertEquals("복지정책과/02-000-0000", row.contact()); // 상세의 문의처가 목록의 전화문의보다 먼저
        assertEquals(Integer.valueOf(12), row.viewCount());
        // 수정일시는 한국 시각 → UTC로 저장 (D-72)
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), row.apiModifiedAt());
    }

    @Test
    void 지원조건_코드는_규칙과_나이로() {
        BenefitRow row = mapper(Set.of()).map(youthRent());
        assertEquals(Integer.valueOf(19), row.ageMin());
        assertEquals(Integer.valueOf(39), row.ageMax());
        assertEquals("PERSONAL", row.targetScope());
        assertEquals(List.of(), JsonText.stringList(row.yCodesJson()));
        Rule age = row.codeRules().stream().filter(r -> r.field().equals("AGE")).findFirst().orElseThrow();
        assertEquals(List.of("19", "39"), age.values());
        assertEquals("19", JsonText.stringMap(row.conditionsRawJson()).get("JA0110"));
    }

    @Test
    void 지역과_신청기간과_상태() {
        BenefitRow row = mapper(Set.of()).map(youthRent());
        assertEquals("11620", row.regionCode());
        assertEquals("PARSED", row.dateStatus());
        assertEquals("DAY", row.datePrecision());
        assertEquals(LocalDate.of(2026, 11, 1), row.applyStart());
        assertEquals(LocalDate.of(2026, 12, 10), row.applyEnd());
        assertEquals("ACTIVE", row.status());
    }

    @Test
    void 기간이_지난_공고는_EXPIRED() {
        ServiceRecord r = youthRent();
        r.detail().put("신청기한", "2025.3.1.~2025.3.31.");
        BenefitRow row = mapper(Set.of()).map(r);
        assertEquals("EXPIRED", row.status());
    }

    @Test
    void 상시신청은_날짜_없이_ALWAYS() {
        BenefitRow row = mapper(Set.of()).map(SyncFixtures.record("S1", "상시 혜택", "중앙행정기관", "보건복지부",
                "1352000", "20261101090000"));
        assertEquals("ALWAYS", row.dateStatus());
        assertNull(row.applyStart());
        assertNull(row.datePrecision());
        assertNull(row.regionCode());
        assertEquals("ACTIVE", row.status());
    }

    @Test
    void 금액() {
        BenefitRow row = mapper(Set.of()).map(youthRent());
        assertEquals(Integer.valueOf(200_000), row.maxAmount());
        assertEquals("MONTH", row.amountPeriod());
        assertEquals(Integer.valueOf(2_400_000), row.annualAmount());
        assertEquals(BenefitMapper.AMOUNT_CONFIDENCE, row.amountConfidence());
    }

    @Test
    void 구비서류는_내가_낼_서류와_공무원_확인_서류() {
        BenefitRow row = mapper(Set.of()).map(youthRent());
        List<Map<String, Object>> docs = JsonText.objectList(row.documentsJson());
        assertEquals(4, docs.size());
        assertEquals(Integer.valueOf(3), row.selfDocCount()); // 등본, 통장사본, 지원신청서
        assertEquals(1L, Long.parseLong(docs.get(0).get("documentTypeId").toString()));
        assertEquals("OFFICIAL", docs.get(3).get("submitType"));
        assertEquals("가족관계증명서", docs.get(3).get("rawName"));
        assertFalse(row.onlineApply());
    }

    @Test
    void 중복_지원_불가_문구() {
        ServiceRecord r = youthRent();
        r.detail().put("지원대상", "관내 거주 청년\n※ 타 월세 지원사업과 중복 지원 불가");
        BenefitRow row = mapper(Set.of()).map(r);
        List<Map<String, Object>> ex = JsonText.objectList(row.exclusionsJson());
        assertEquals(1, ex.size());
        assertTrue(ex.get(0).get("sourceQuote").toString().contains("중복 지원 불가"));
    }

    @Test
    void 해시태그는_사전에_있는_것만_남기고_나머지는_후보() {
        BenefitRow all = mapper(Set.of()).map(youthRent());
        List<String> tags = JsonText.stringList(all.hashtagsJson());
        assertFalse(tags.isEmpty());

        BenefitRow filtered = mapper(Set.of(tags.get(0))).map(youthRent());
        assertEquals(List.of(tags.get(0)), JsonText.stringList(filtered.hashtagsJson()));
        assertEquals(tags.subList(1, tags.size()), JsonText.stringList(filtered.hashtagCandidatesJson()));
    }

    @Test
    void 필수_칸이_깨지면_격리() {
        ServiceRecord r = youthRent();
        r.list().put("수정일시", "2026-11");
        r.detail().put("수정일시", null);
        assertThrows(BenefitMapper.InvalidRecordException.class, () -> mapper(Set.of()).map(r));
    }

    @Test
    void 법인_전용은_CORP() {
        ServiceRecord r = youthRent();
        r.list().put("사용자구분", "법인/시설/단체");
        assertEquals("CORP", mapper(Set.of()).map(r).targetScope());
    }
}
