package com.bokdeongi.backend.eligibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 아깝게 탈락 (BEN-05, D-80).
 *
 * <p>해당 없음 중에서 미충족이 정확히 1개이고, 그 조건이 시간이 지나면 저절로 충족되는 나이 하한
 * (본인 AGE, 막내 자녀 YOUNGEST_CHILD_AGE)이며, 충족되는 날이 신청 마감 전(상시 신청 포함)인 경우만 해당한다.
 * 주거·소득처럼 언제 바뀔지 알 수 없는 조건은 넣지 않는다.
 *
 * @param condition       충족되면 되는 조건 하나
 * @param eligibleFrom    그 조건을 충족하는 날 ("2027-03-02부터 신청 가능")
 * @param othersNeedCheck 다른 조건 중 아직 확인이 필요한(UNKNOWN) 것이 있으면 true. 그날이 와도 '조건부'다
 */
public record NearMiss(ConditionTrace condition, LocalDate eligibleFrom, boolean othersNeedCheck) {

    /**
     * @param profile    판정에 쓴 회원 정보
     * @param rules      판정에 쓴 규칙 (judge에 넘긴 순서 그대로)
     * @param result     judge 결과. trace의 앞부분이 rules와 같은 순서여야 한다
     * @param today      판정 기준일
     * @param applyEnd   신청 마감일 (benefits.apply_end). 모르면 null
     * @param alwaysOpen 상시 신청이면 true (benefits.date_status = ALWAYS)
     */
    public static Optional<NearMiss> find(ProfileSnapshot profile, List<Rule> rules, EligibilityResult result,
            LocalDate today, LocalDate applyEnd, boolean alwaysOpen) {
        if (result.verdict() != Verdict.INELIGIBLE || result.failedCount() != 1) {
            return Optional.empty();
        }
        List<ConditionTrace> trace = result.trace();
        Map<Integer, List<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < trace.size(); i++) {
            groups.computeIfAbsent(trace.get(i).group(), g -> new ArrayList<>()).add(i);
        }
        List<Integer> common = groups.getOrDefault(0, List.of());
        List<List<Integer>> scopes = new ArrayList<>();
        groups.forEach((group, indexes) -> {
            if (group > 0) {
                List<Integer> scope = new ArrayList<>(common);
                scope.addAll(indexes);
                scopes.add(scope);
            }
        });
        if (scopes.isEmpty()) {
            scopes.add(common);
        }

        NearMiss best = null;
        for (List<Integer> scope : scopes) {
            List<Integer> failed = scope.stream().filter(i -> trace.get(i).outcome() == Outcome.FAIL).toList();
            if (failed.size() != 1 || failed.get(0) >= rules.size()) {
                continue;
            }
            int index = failed.get(0);
            Optional<LocalDate> date = dateWhenMet(profile, rules.get(index), today);
            if (date.isEmpty() || !beforeDeadline(date.get(), applyEnd, alwaysOpen)) {
                continue;
            }
            boolean unknown = scope.stream().anyMatch(i -> trace.get(i).outcome() == Outcome.UNKNOWN);
            NearMiss candidate = new NearMiss(trace.get(index), date.get(), unknown);
            if (best == null || better(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 확인할 것이 없는 후보, 그다음 더 빨리 충족되는 후보를 고른다. */
    private static boolean better(NearMiss a, NearMiss b) {
        if (a.othersNeedCheck() != b.othersNeedCheck()) {
            return !a.othersNeedCheck();
        }
        return a.eligibleFrom().isBefore(b.eligibleFrom());
    }

    private static boolean beforeDeadline(LocalDate date, LocalDate applyEnd, boolean alwaysOpen) {
        if (alwaysOpen) {
            return true;
        }
        return applyEnd != null && !date.isAfter(applyEnd);
    }

    /** 시간이 지나면 충족되는 나이 하한이면 충족되는 날, 아니면 빈 값. */
    static Optional<LocalDate> dateWhenMet(ProfileSnapshot p, Rule rule, LocalDate today) {
        Optional<BigDecimal[]> bounds = lowerAndUpper(rule);
        if (bounds.isEmpty()) {
            return Optional.empty();
        }
        int lower = bounds.get()[0].intValueExact();
        BigDecimal upper = bounds.get()[1];
        LocalDate date;
        switch (rule.field()) {
            case "AGE" -> {
                if (p.birthDate() == null) {
                    return Optional.empty();
                }
                date = p.birthDate().plusYears(lower);     // 만 나이: 생일에 한 살 (엔진의 Period 계산과 같음)
            }
            case "YOUNGEST_CHILD_AGE" -> {
                if (p.youngestChildBirthYear() == null) {
                    return Optional.empty();
                }
                date = LocalDate.of(p.youngestChildBirthYear() + lower, 1, 1);   // 엔진이 연 나이로 비교하므로 그해 1월 1일
            }
            default -> {
                return Optional.empty();
            }
        }
        if (!date.isAfter(today)) {
            return Optional.empty();   // 이미 하한을 넘었는데 탈락 = 상한 초과 등 다른 이유
        }
        if (upper != null && upper.compareTo(BigDecimal.valueOf(lower)) < 0) {
            return Optional.empty();
        }
        return Optional.of(date);
    }

    /** GTE는 [하한, 없음], BETWEEN은 [하한, 상한]. 하한이 정수가 아니면 대상 아님. */
    private static Optional<BigDecimal[]> lowerAndUpper(Rule rule) {
        try {
            return switch (rule.operator()) {
                case GTE -> rule.values().size() == 1
                        ? Optional.of(new BigDecimal[] {integer(rule.values().get(0)), null})
                        : Optional.empty();
                case BETWEEN -> rule.values().size() == 2
                        ? Optional.of(new BigDecimal[] {integer(rule.values().get(0)), new BigDecimal(rule.values().get(1))})
                        : Optional.empty();
                default -> Optional.empty();
            };
        } catch (ArithmeticException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal integer(String value) {
        BigDecimal number = new BigDecimal(value);
        number.intValueExact();
        return number;
    }
}
