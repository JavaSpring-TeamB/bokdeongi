package com.bokdeongi.eligibility;

import java.util.Map;

/** 시·도 이름을 한 가지로 맞춘다. 공고 데이터와 주소 API의 표기가 달라도 같은 지역으로 비교하기 위해서다 (D-56). */
public final class RegionNames {

    /** 옛 이름·줄임말 → 정식 이름. 2026-07-01 전라남도·광주광역시는 전남광주통합특별시로 통합됐다. */
    private static final Map<String, String> SIDO_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("제주도", "제주특별자치도"),
            Map.entry("제주", "제주특별자치도"),
            Map.entry("전라남도", "전남광주통합특별시"),
            Map.entry("전남", "전남광주통합특별시"),
            Map.entry("광주광역시", "전남광주통합특별시"));

    private RegionNames() {
    }

    public static String normalizeSido(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return SIDO_ALIASES.getOrDefault(trimmed, trimmed);
    }

    /**
     * 회원 지역이 규칙 지역에 들어가는지.
     *
     * @param ruleRegion "부산광역시" 또는 "부산광역시 부산진구"
     */
    public static boolean contains(String ruleRegion, String userSido, String userSigungu) {
        String[] parts = ruleRegion.trim().split("\\s+", 2);
        String ruleSido = normalizeSido(parts[0]);
        if (!ruleSido.equals(normalizeSido(userSido))) {
            return false;
        }
        if (parts.length == 1) {
            return true; // 시·도 전체 대상
        }
        // "수원시"(공고)와 "수원시 장안구"(주소)처럼 일반구가 붙어도 같은 시로 본다
        String ruleSigungu = parts[1].trim();
        return userSigungu != null && (userSigungu.trim().equals(ruleSigungu) || userSigungu.trim().startsWith(ruleSigungu + " "));
    }
}
