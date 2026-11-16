package com.bokdeongi.backend.eligibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 소관기관 지역(benefits.region_code, D-56)을 지역 규칙으로 붙인다.
 *
 * <p>기본은 공통 묶음(0)에 붙여 모든 경우에 적용한다. 원문이 일부 묶음에만 지역을 따로 정한 경우
 * ("거주지 무관"이면 기관의 시·도 전체, D-88), 그 묶음에는 원문 지역을 쓰고 나머지 묶음에만 기관 지역을 붙인다.
 * 공통 묶음에 붙이면 그 묶음까지 기관 지역으로 다시 좁혀지기 때문이다.
 */
public final class OrgRegion {

    public static final String FIELD = "REGION";

    private OrgRegion() {
    }

    /**
     * @param rules     라벨·추출로 만든 규칙
     * @param orgRegion 기관 지역 ("서울특별시 관악구", "부산광역시"). 중앙행정기관·공공기관(전국)은 null
     * @return 기관 지역 규칙을 붙인 새 목록 (입력은 바꾸지 않는다)
     */
    public static List<Rule> apply(List<Rule> rules, String orgRegion) {
        if (orgRegion == null || orgRegion.isBlank()) {
            return List.copyOf(rules);
        }
        Set<Integer> withRegion = new TreeSet<>();
        Set<Integer> alternatives = new TreeSet<>();
        for (Rule rule : rules) {
            if (FIELD.equals(rule.field())) {
                withRegion.add(rule.group());
            }
            if (rule.group() > 0) {
                alternatives.add(rule.group());
            }
        }
        List<Rule> result = new ArrayList<>(rules);
        if (withRegion.contains(0)) {
            return List.copyOf(result); // 공통 지역을 원문이 이미 정했다
        }
        if (withRegion.isEmpty()) {
            result.add(regionRule(orgRegion, 0));
            return List.copyOf(result);
        }
        for (int group : alternatives) {
            if (!withRegion.contains(group)) {
                result.add(regionRule(orgRegion, group));
            }
        }
        return List.copyOf(result);
    }

    private static Rule regionRule(String orgRegion, int group) {
        return new Rule(null, FIELD, Operator.IN, List.of(orgRegion.trim()), "CODE", "소관기관 지역: " + orgRegion.trim(),
                null, group);
    }
}
