package com.bokdeongi.backend.sync;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 소관기관 → 지역 코드(benefits.region_code, regions의 법정동 코드) (D-56).
 *
 * <p>방법은 data-lab/reports/region_check.md 실측과 같다.
 * <ol>
 *   <li>중앙행정기관·공공기관은 전국(null)</li>
 *   <li>시군구·광역시도 공고의 기관명("서울특별시 관악구")으로 '기관코드 → 시·도, 시·군·구' 표를 만든다.
 *       지방공기업·출연기관은 상위 지자체와 같은 기관코드를 써서 이 표로 찾는다</li>
 *   <li>표에 없으면 기관명 앞의 시·도로 찾는다 (교육청: "부산광역시교육청")</li>
 *   <li>시·군·구가 regions에 없으면(2026-07 개편으로 사라진 인천 중구·동구·서구 등) 시·도 코드로 넓힌다</li>
 * </ol>
 * 옛 시·도 이름(전라남도·광주광역시·강원도·전라북도)은 지금 이름으로 바꾼다.
 */
public final class OrgRegionResolver {

    /** regions 한 줄 */
    public record RegionRow(String code, String sido, String sigungu) {
    }

    /**
     * @param code     region_code. 전국이면 null
     * @param found    지자체 기관인데 지역을 못 찾았으면 false (전국으로 잘못 퍼지지 않게 기록한다)
     * @param widened  시·군·구를 못 찾아 시·도로 넓혔으면 true
     */
    public record Resolved(String code, boolean found, boolean widened) {
        static final Resolved NATIONWIDE = new Resolved(null, true, false);
    }

    private static final List<String> NATIONWIDE_TYPES = List.of("중앙행정기관", "공공기관");
    private static final List<String> TABLE_TYPES = List.of("시군구", "광역시도");

    /** (지금 이름, 옛 이름·줄임말). 줄임말은 뒤에 띄어쓰기가 있거나 이름 전체일 때만 인정한다 */
    private static final String[][] SIDO = {
        {"서울특별시", "서울"}, {"부산광역시", "부산"}, {"대구광역시", "대구"}, {"인천광역시", "인천"},
        {"대전광역시", "대전"}, {"울산광역시", "울산"}, {"세종특별자치시", "세종"}, {"경기도", "경기"},
        {"강원특별자치도", "강원도", "강원"}, {"충청북도", "충북"}, {"충청남도", "충남"},
        {"전북특별자치도", "전라북도", "전북"}, {"경상북도", "경북"}, {"경상남도", "경남"},
        {"전남광주통합특별시", "전라남도", "광주광역시", "전남"}, {"제주특별자치도", "제주도", "제주"},
    };
    private static final Pattern SIGUNGU = Pattern.compile("[가-힣]+(?:시|군|구)");

    private final Map<String, String[]> codeTable = new HashMap<>();
    private final Map<String, String> regionCodes = new HashMap<>();

    /**
     * @param records 이번에 받은 공고 전체 (기관코드 표를 만든다)
     * @param regions regions 테이블 전체
     */
    public OrgRegionResolver(Collection<ServiceRecord> records, Collection<RegionRow> regions) {
        for (RegionRow r : regions) {
            regionCodes.put(key(r.sido(), r.sigungu()), r.code());
        }
        for (ServiceRecord rec : records) {
            if (!TABLE_TYPES.contains(rec.listText("소관기관유형"))) {
                continue;
            }
            String name = rec.listText("소관기관명");
            String code = rec.listText("소관기관코드");
            String[] place = place(name);
            if (code != null && place != null) {
                codeTable.put(code, place);
            }
        }
    }

    public Resolved resolve(ServiceRecord rec) {
        String type = rec.listText("소관기관유형");
        if (type != null && NATIONWIDE_TYPES.contains(type)) {
            return Resolved.NATIONWIDE;
        }
        String[] place = codeTable.get(rec.listText("소관기관코드"));
        if (place == null) {
            place = place(rec.text("소관기관명"));
        }
        if (place == null) {
            // 유형이 비었거나 처음 보는 유형인데 이름에 시·도도 없으면 전국 기관으로 본다
            return type == null || !isLocal(type) ? Resolved.NATIONWIDE : new Resolved(null, false, false);
        }
        String exact = place[1] == null ? null : regionCodes.get(key(place[0], place[1]));
        if (exact == null && place[1] != null && cityOnly(place[1]) != null) {
            exact = regionCodes.get(key(place[0], cityOnly(place[1])));
        }
        if (exact != null) {
            return new Resolved(exact, true, false);
        }
        String sido = regionCodes.get(key(place[0], null));
        if (sido == null) {
            return new Resolved(null, false, false);
        }
        return new Resolved(sido, true, place[1] != null);
    }

    private static boolean isLocal(String type) {
        return List.of("시군구", "광역시도", "교육청", "지방공기업", "지방출자_출연기관").contains(type);
    }

    /** 기관명 → {시·도 지금 이름, 시·군·구 또는 null}. 시·도를 못 찾으면 null */
    static String[] place(String orgName) {
        if (orgName == null) {
            return null;
        }
        String text = orgName.strip();
        for (String[] names : SIDO) {
            for (int i = 0; i < names.length; i++) {
                String n = names[i];
                boolean fullName = i == 0 || n.endsWith("도") || n.endsWith("시");
                boolean matches = fullName ? text.startsWith(n) : (text.equals(n) || text.startsWith(n + " "));
                if (matches) {
                    String rest = text.substring(n.length()).strip();
                    return new String[] {names[0], sigungu(rest)};
                }
            }
        }
        return null;
    }

    /** 시·도 뒤 첫 낱말이 시·군·구로 끝나면 그 낱말 ("관악구", "수원시") */
    static String sigungu(String rest) {
        if (rest.isEmpty()) {
            return null;
        }
        String first = rest.split("\\s+")[0];
        return SIGUNGU.matcher(first).matches() ? first : null;
    }

    /** "수원시장안구"처럼 일반구가 붙어 있으면 시까지만 ("수원시"). 아니면 null */
    static String cityOnly(String sigungu) {
        int i = sigungu.indexOf('시');
        return i > 0 && i < sigungu.length() - 1 ? sigungu.substring(0, i + 1) : null;
    }

    private static String key(String sido, String sigungu) {
        return sido + "|" + (sigungu == null ? "" : sigungu);
    }
}
