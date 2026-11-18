package com.bokdeongi.backend.sync;

import com.bokdeongi.backend.sync.OrgRegionResolver.RegionRow;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 동기화 테스트용 API 응답 조각 (실제 API 칸 이름 그대로) */
final class SyncFixtures {

    private SyncFixtures() {
    }

    static final List<RegionRow> REGIONS = List.of(
            new RegionRow("11", "서울특별시", null),
            new RegionRow("11620", "서울특별시", "관악구"),
            new RegionRow("11140", "서울특별시", "중구"),
            new RegionRow("26", "부산광역시", null),
            new RegionRow("26230", "부산광역시", "부산진구"),
            new RegionRow("28", "인천광역시", null),
            new RegionRow("28125", "인천광역시", "제물포구"),
            new RegionRow("12", "전남광주통합특별시", null),
            new RegionRow("12300", "전남광주통합특별시", "북구"),
            new RegionRow("12110", "전남광주통합특별시", "목포시"),
            new RegionRow("41", "경기도", null),
            new RegionRow("41110", "경기도", "수원시"),
            new RegionRow("36110", "세종특별자치시", null));

    /** 목록 한 건 */
    static Map<String, Object> list(String id, String title, String orgType, String orgName, String orgCode,
            String modified) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("서비스ID", id);
        m.put("서비스명", title);
        m.put("서비스목적요약", title + " 요약");
        m.put("지원유형", "현금");
        m.put("지원대상", "관내 거주 청년");
        m.put("지원내용", "월 최대 20만원 지원");
        m.put("신청방법", "방문 신청");
        m.put("신청기한유형", "상시신청");
        m.put("신청기한", "상시신청");
        m.put("소관기관유형", orgType);
        m.put("소관기관명", orgName);
        m.put("소관기관코드", orgCode);
        m.put("부서명", "복지정책과");
        m.put("전화문의", "02-000-0000");
        m.put("상세조회URL", "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/" + id);
        m.put("서비스분야", "주거·자립");
        m.put("사용자구분", "개인");
        m.put("조회수", "12");
        m.put("수정일시", modified);
        m.put("등록일시", "20250101000000");
        return m;
    }

    static Map<String, Object> detail(String id, String title, String modified) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("서비스ID", id);
        m.put("서비스명", title);
        m.put("지원대상", "관내 거주 청년");
        m.put("지원내용", "월 최대 20만원 지원");
        m.put("신청방법", "방문 신청");
        m.put("신청기한유형", "상시신청");
        m.put("신청기한", "상시신청");
        m.put("구비서류", "주민등록등본, 통장사본, 지원신청서");
        m.put("공무원확인구비서류", "가족관계증명서");
        m.put("문의처", "복지정책과/02-000-0000");
        m.put("소관기관명", "서울특별시 관악구");
        m.put("수정일시", modified);
        return m;
    }

    /** 지원조건: 나이 19~39, 나머지 그룹은 조건 없음(빈 값) */
    static Map<String, Object> conditions(String id) {
        Map<String, Object> m = new HashMap<>();
        m.put("서비스ID", id);
        m.put("JA0110", "19");
        m.put("JA0111", "39");
        m.put("JA0101", null);
        m.put("JA0102", null);
        return m;
    }

    static ServiceRecord record(String id, String title, String orgType, String orgName, String orgCode,
            String modified) {
        Map<String, Object> d = detail(id, title, modified);
        d.put("소관기관명", orgName);
        return new ServiceRecord(list(id, title, orgType, orgName, orgCode, modified), d, conditions(id));
    }
}
