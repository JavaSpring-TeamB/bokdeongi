package com.bokdeongi.backend.eligibility.service;

import com.bokdeongi.backend.eligibility.ConditionTrace;
import com.bokdeongi.backend.eligibility.EligibilityJudge;
import com.bokdeongi.backend.eligibility.EligibilityResult;
import com.bokdeongi.backend.eligibility.NearMiss;
import com.bokdeongi.backend.eligibility.Operator;
import com.bokdeongi.backend.eligibility.OrgRegion;
import com.bokdeongi.backend.eligibility.ProfileSnapshot;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.Verdict;
import com.bokdeongi.backend.eligibility.store.BenefitTarget;
import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.eligibility.store.ProfileRow;
import com.bokdeongi.backend.eligibility.store.ResultRow;
import com.bokdeongi.backend.eligibility.store.RuleRow;
import com.bokdeongi.backend.eligibility.store.StoredResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 회원 한 명 × 혜택 전체 판정 (ELIG-01). DB를 모르는 순수 계산이라 단위 테스트로 검증한다.
 *
 * <p>읽기·쓰기는 {@code EligibilityStore}, 트랜잭션·호출 시점은 {@code EligibilityService}가 맡는다.
 */
public final class EligibilityBatch {

    /** 개인 추천에서 빼는 공고 (D-49, D-87) */
    static final String SCOPE_CORP = "CORP";
    /** 소상공인 공고: 자영업 회원만 조건부로 보여 준다 (D-49) */
    static final String SCOPE_SMALL_BIZ = "SMALL_BIZ";
    static final String SMALL_BIZ_QUOTE = "소상공인 대상 공고: 업종·매출 같은 자격은 공고에서 확인하세요";
    /** 규칙 구조화 실패 → 규칙이 빠졌을 수 있다 */
    static final String RULES_FAILED = "FAILED";
    static final String DATE_ALWAYS = "ALWAYS";

    private final EligibilityJudge judge;

    public EligibilityBatch(EligibilityJudge judge) {
        this.judge = judge;
    }

    /**
     * @param regionNames region_code → "서울특별시 관악구" 또는 "서울특별시" (regions)
     * @param existing    이미 저장된 결과 (benefit_id별)
     * @param today       판정 기준일 (한국 날짜)
     * @param now         저장 시각 (UTC)
     * @return 저장할 결과. 개인 추천 대상이 아닌 공고는 빠진다
     */
    public List<ResultRow> judgeAll(ProfileRow profileRow, List<BenefitTarget> benefits, Map<Long, List<RuleRow>> rules,
            Map<String, String> regionNames, Map<Long, StoredResult> existing, LocalDate today, LocalDateTime now) {
        ProfileSnapshot profile = toSnapshot(profileRow);
        boolean selfEmployed = profile.statusCodes() != null && profile.statusCodes().contains(ProfileSnapshot.SELF_EMPLOYED);
        List<ResultRow> out = new ArrayList<>();
        for (BenefitTarget benefit : benefits) {
            if (SCOPE_CORP.equals(benefit.targetScope())
                    || (SCOPE_SMALL_BIZ.equals(benefit.targetScope()) && !selfEmployed)) {
                continue;
            }
            List<Rule> ruleList = assemble(benefit, rules.getOrDefault(benefit.id(), List.of()), regionNames);
            EligibilityResult result = judge.judge(profile, ruleList, today, RULES_FAILED.equals(benefit.rulesStatus()));
            Optional<NearMiss> nearMiss = NearMiss.find(profile, ruleList, result, today, benefit.applyEnd(),
                    DATE_ALWAYS.equals(benefit.dateStatus()));
            out.add(toRow(benefit.id(), result, nearMiss, existing.get(benefit.id()), profileRow.profileVersion(), now));
        }
        return out;
    }

    // ---------------------------------------------------------------- 회원

    /** users 칸 → 판정 엔진 입력. JSON 배열 칸은 NULL(건너뜀)과 [](해당 없음)을 구분한다 (D-45). */
    static ProfileSnapshot toSnapshot(ProfileRow r) {
        return new ProfileSnapshot(r.birthDate(), r.gender(), r.sidoName(), r.sigunguName(), r.medianIncomePct(),
                r.householdSize(), toSet(JsonText.stringList(r.householdTypeCodesJson())),
                toSet(JsonText.stringList(r.statusCodesJson())), toSet(JsonText.stringList(r.sensitiveCodesJson())),
                r.housingType(), r.hasHouse(),
                r.maritalStatus(), r.childrenCount(), r.youngestChildBirthYear(), r.welfareStatus(),
                JsonText.stringMap(r.answersJson()));
    }

    private static Set<String> toSet(List<String> list) {
        return list == null ? null : new HashSet<>(list);
    }

    // ---------------------------------------------------------------- 규칙

    /**
     * 혜택 한 건의 규칙 = benefit_rules + 규칙으로 못 바꾼 조건(D-64) + 소상공인 확인(D-49) + 기관 지역(D-56, D-88).
     * 형식이 잘못된 줄도 버리지 않는다: 엔진이 '해석 불가'로 조건부 처리하게 넘긴다 (조건을 몰래 빼면 해당으로 잘못 판정됨).
     */
    static List<Rule> assemble(BenefitTarget benefit, List<RuleRow> rows, Map<String, String> regionNames) {
        List<Rule> rules = new ArrayList<>();
        for (RuleRow row : rows) {
            rules.add(toRule(row));
        }
        for (Map<String, Object> u : JsonText.objectList(benefit.unparsedJson())) {
            Object quote = u.get("sourceQuote");
            rules.add(Rule.unparsed(groupOf(u.get("ruleGroup")), quote == null ? "" : quote.toString()));
        }
        if (SCOPE_SMALL_BIZ.equals(benefit.targetScope())) {
            rules.add(Rule.unparsed(0, SMALL_BIZ_QUOTE));
        }
        String region = benefit.regionCode() == null ? null : regionNames.get(benefit.regionCode());
        return OrgRegion.apply(rules, region);
    }

    static Rule toRule(RuleRow row) {
        List<String> values;
        Operator op;
        try {
            values = JsonText.stringList(row.valueJson());
            op = Operator.valueOf(row.operator());
        } catch (RuntimeException e) {
            // 값·연산자를 못 읽으면 엔진이 모르는 필드로 넘겨 UNSUPPORTED_RULE(조건부)이 되게 한다
            return new Rule(row.id(), "INVALID:" + row.field(), Operator.EQ, List.of(), row.source(),
                    row.sourceQuote(), row.confidence(), row.group());
        }
        return new Rule(row.id(), row.field(), op, values == null ? List.of() : values, row.source(),
                row.sourceQuote(), row.confidence(), row.group());
    }

    private static int groupOf(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, (int) Double.parseDouble(value.toString()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- 결과

    static ResultRow toRow(long benefitId, EligibilityResult result, Optional<NearMiss> nearMiss, StoredResult stored,
            int profileVersion, LocalDateTime now) {
        String verdict = result.verdict().name();
        LocalDateTime firstEligible = stored == null ? null : stored.firstEligibleAt();
        if (firstEligible == null && result.verdict() == Verdict.ELIGIBLE) {
            firstEligible = now; // 처음 해당된 시각은 다시 판정해도 유지 (HOME-02 신규 배지)
        }
        return new ResultRow(benefitId, verdict, stored == null ? null : stored.result(), nearMiss.isPresent(),
                nearMiss.map(EligibilityBatch::nearMissReason).orElse(null),
                JsonText.writeStrings(result.missingFields()), traceJson(result.trace()), profileVersion,
                firstEligible, now, stored != null);
    }

    /** "2027년 3월 2일부터 나이 조건을 충족해요" (eligibility_results.near_miss_reason, 200자 이내) */
    static String nearMissReason(NearMiss n) {
        LocalDate d = n.eligibleFrom();
        String label = switch (n.condition().field()) {
            case "AGE" -> "나이";
            case "YOUNGEST_CHILD_AGE" -> "막내 자녀 나이";
            default -> n.condition().field();
        };
        String text = d.getYear() + "년 " + d.getMonthValue() + "월 " + d.getDayOfMonth() + "일부터 " + label
                + " 조건을 충족해요" + (n.othersNeedCheck() ? " (다른 조건은 확인 필요)" : "");
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    /** 판정 근거 → [{ruleId, field, expected, actual, outcome, reason, sourceQuote, boundary, group}] (테이블 정의서와 같은 모양) */
    static String traceJson(List<ConditionTrace> trace) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < trace.size(); i++) {
            ConditionTrace t = trace.get(i);
            if (i > 0) {
                sb.append(',');
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", t.ruleId());
            m.put("field", t.field());
            m.put("expected", t.expected());
            m.put("actual", t.actual());
            m.put("outcome", t.outcome().name());
            m.put("reason", t.reason() == null ? null : t.reason().name());
            m.put("sourceQuote", t.sourceQuote());
            m.put("boundary", t.boundary());
            m.put("group", t.group());
            JsonText.writeObject(sb, m);
        }
        return sb.append(']').toString();
    }
}
