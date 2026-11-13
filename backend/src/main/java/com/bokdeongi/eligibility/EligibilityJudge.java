package com.bokdeongi.eligibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 규칙 엔진 (ELIG-01). 프로필 + 규칙 → 3값 판정 + 조건별 근거.
 *
 * <p>DB·Spring·LLM에 의존하지 않는 순수 계산이다 (D-55). 같은 입력이면 항상 같은 결과를 낸다.
 * 자격 판정은 이 클래스만 한다 (AGENTS.md 원칙 1).
 *
 * <ul>
 *   <li>결합 규칙 (D-53): FAIL이 하나라도 있으면 INELIGIBLE, 없고 UNKNOWN이 있으면 CONDITIONAL, 전부 PASS면 ELIGIBLE</li>
 *   <li>미입력 (D-45): 회원 값이 null이면 UNKNOWN</li>
 *   <li>소득 경계 (D-54): 기준 ±5%p 안이면 UNKNOWN</li>
 *   <li>지원조건 코드 그룹 (D-48): JA_GROUP 규칙은 Y 코드 중 하나라도 해당하면 PASS</li>
 * </ul>
 */
public final class EligibilityJudge {

    /** D-54: 소득 % 경계 폭. */
    public static final BigDecimal INCOME_MARGIN = new BigDecimal("5");
    /** LLM 규칙을 믿는 최소 신뢰도. 이보다 낮으면 UNKNOWN. */
    public static final BigDecimal DEFAULT_MIN_CONFIDENCE = new BigDecimal("0.70");

    // 지원조건 JA 코드 분류 (노션 DATA-02 대응표, D-48·D-50)
    static final Set<String> JA03_SENSITIVE = Set.of("JA0301", "JA0302", "JA0303", "JA0328", "JA0329", "JA0330");
    static final Set<String> JA04_SENSITIVE = Set.of("JA0401", "JA0402");
    static final String NO_HOUSE_CODE = "JA0412";
    /** 중위소득 구간 (하한 초과, 상한 이하). JA0201은 0% 이상. */
    static final Map<String, BigDecimal[]> INCOME_BRACKETS = Map.of(
            "JA0201", new BigDecimal[] {null, new BigDecimal("50")},
            "JA0202", new BigDecimal[] {new BigDecimal("50"), new BigDecimal("75")},
            "JA0203", new BigDecimal[] {new BigDecimal("75"), new BigDecimal("100")},
            "JA0204", new BigDecimal[] {new BigDecimal("100"), new BigDecimal("200")},
            "JA0205", new BigDecimal[] {new BigDecimal("200"), null});

    private static final String HIDDEN = "비공개";

    private final BigDecimal minConfidence;

    public EligibilityJudge() {
        this(DEFAULT_MIN_CONFIDENCE);
    }

    public EligibilityJudge(BigDecimal minConfidence) {
        this.minConfidence = minConfidence;
    }

    /** 규칙 구조화가 정상인 혜택의 판정. */
    public EligibilityResult judge(ProfileSnapshot profile, List<Rule> rules, LocalDate today) {
        return judge(profile, rules, today, false);
    }

    /**
     * @param rulesIncomplete 공고 조건 구조화가 실패해 규칙이 빠졌을 수 있으면 true (benefits.rules_status = FAILED).
     *                        이 경우 다른 조건을 모두 만족해도 CONDITIONAL이다.
     */
    public EligibilityResult judge(ProfileSnapshot profile, List<Rule> rules, LocalDate today, boolean rulesIncomplete) {
        List<ConditionTrace> trace = new ArrayList<>();
        for (Rule rule : rules) {
            trace.add(evaluate(profile, rule, today));
        }
        if (rulesIncomplete) {
            trace.add(new ConditionTrace(null, "RULES", "공고 조건 전체", "-", Outcome.UNKNOWN,
                    UnknownReason.UNSUPPORTED_RULE, "공고 조건을 구조화하지 못했습니다. 원문을 확인하세요.", false));
        }
        Set<String> missing = new LinkedHashSet<>();
        for (ConditionTrace t : trace) {
            if (t.reason() == UnknownReason.MISSING_VALUE) {
                missing.add(t.field());
            }
        }
        return new EligibilityResult(combine(trace), trace, List.copyOf(missing));
    }

    /** D-53 결합 규칙. */
    static Verdict combine(List<ConditionTrace> trace) {
        boolean unknown = false;
        for (ConditionTrace t : trace) {
            if (t.outcome() == Outcome.FAIL) {
                return Verdict.INELIGIBLE;
            }
            if (t.outcome() == Outcome.UNKNOWN) {
                unknown = true;
            }
        }
        return unknown ? Verdict.CONDITIONAL : Verdict.ELIGIBLE;
    }

    ConditionTrace evaluate(ProfileSnapshot p, Rule rule, LocalDate today) {
        if (rule.fromLlm() && rule.confidence() != null && rule.confidence().compareTo(minConfidence) < 0) {
            return unknown(rule, describe(rule), "-", UnknownReason.LOW_CONFIDENCE, false);
        }
        String field = rule.field();
        try {
            if (field.startsWith("ANSWER:")) {
                String answer = p.answers().get(field.substring("ANSWER:".length()));
                return compareText(rule, answer, false);
            }
            if (field.startsWith("JA_GROUP:")) {
                return jaGroup(p, rule, field.substring("JA_GROUP:".length()));
            }
            return switch (field) {
                case "AGE" -> compareNumber(rule, p.birthDate() == null ? null
                        : BigDecimal.valueOf(Period.between(p.birthDate(), today).getYears()));
                case "GENDER" -> compareText(rule, p.gender(), false);
                case "REGION" -> region(p, rule);
                case "INCOME_PCT" -> income(p, rule);
                case "HOUSEHOLD_SIZE" -> compareNumber(rule, toDecimal(p.householdSize()));
                case "HOUSEHOLD_TYPE" -> codes(rule, p.householdTypeCodes(), false);
                case "STATUS" -> codes(rule, p.statusCodes(), false);
                case "SENSITIVE" -> codes(rule, p.sensitiveCodes(), true);
                case "HOUSING" -> compareText(rule, p.housingType(), false);
                case "HAS_HOUSE" -> compareText(rule, p.hasHouse() == null ? null : p.hasHouse().toString(), false);
                case "MARITAL" -> compareText(rule, p.maritalStatus(), false);
                case "CHILDREN_COUNT" -> compareNumber(rule, toDecimal(p.childrenCount()));
                case "YOUNGEST_CHILD_AGE" -> youngestChild(p, rule, today);
                case "WELFARE" -> compareText(rule, p.welfareStatus(), false);
                default -> unknown(rule, describe(rule), "-", UnknownReason.UNSUPPORTED_RULE, false);
            };
        } catch (IllegalArgumentException e) {
            // 규칙 값이 숫자가 아니거나 BETWEEN 값 개수가 틀린 경우. 판정을 멈추지 않고 원문 확인으로 넘긴다
            return unknown(rule, describe(rule), "-", UnknownReason.UNSUPPORTED_RULE, false);
        }
    }

    // ---------- 필드별 판정 ----------

    private ConditionTrace income(ProfileSnapshot p, Rule rule) {
        BigDecimal pct = p.medianIncomePct();
        if (pct == null) {
            return unknown(rule, describe(rule), "미입력", UnknownReason.MISSING_VALUE, false);
        }
        Predicate<BigDecimal> passes = rule.operator() == Operator.IN
                ? value -> rule.values().stream().anyMatch(code -> inBracket(value, code))
                : value -> numberMatches(rule, value);
        // D-54: 기준 ±5%p 안에서 결과가 바뀌면 경계값
        boolean now = passes.test(pct);
        BigDecimal low = pct.subtract(INCOME_MARGIN).max(BigDecimal.ZERO);
        BigDecimal high = pct.add(INCOME_MARGIN);
        if (passes.test(low) != now || passes.test(high) != now) {
            return unknown(rule, describe(rule), pct.stripTrailingZeros().toPlainString() + "%", UnknownReason.BOUNDARY, true);
        }
        return result(rule, describe(rule), pct.stripTrailingZeros().toPlainString() + "%", now);
    }

    static boolean inBracket(BigDecimal pct, String code) {
        BigDecimal[] range = INCOME_BRACKETS.get(code);
        if (range == null) {
            throw new IllegalArgumentException("알 수 없는 소득 구간 코드: " + code);
        }
        boolean aboveLow = range[0] == null ? pct.signum() >= 0 : pct.compareTo(range[0]) > 0;
        boolean belowHigh = range[1] == null || pct.compareTo(range[1]) <= 0;
        return aboveLow && belowHigh;
    }

    private ConditionTrace region(ProfileSnapshot p, Rule rule) {
        if (p.sidoName() == null) {
            return unknown(rule, describe(rule), "미입력", UnknownReason.MISSING_VALUE, false);
        }
        boolean pass = rule.values().stream().anyMatch(r -> RegionNames.contains(r, p.sidoName(), p.sigunguName()));
        String actual = p.sigunguName() == null ? p.sidoName() : p.sidoName() + " " + p.sigunguName();
        return result(rule, String.join(", ", rule.values()), actual, pass);
    }

    private ConditionTrace youngestChild(ProfileSnapshot p, Rule rule, LocalDate today) {
        if (p.childrenCount() != null && p.childrenCount() == 0) {
            return result(rule, describe(rule), "자녀 없음", false);
        }
        if (p.youngestChildBirthYear() == null) {
            return unknown(rule, describe(rule), "미입력", UnknownReason.MISSING_VALUE, false);
        }
        // 출생연도만 저장하므로 연 나이로 비교한다
        return compareNumber(rule, BigDecimal.valueOf(today.getYear() - p.youngestChildBirthYear()));
    }

    /** STATUS·HOUSEHOLD_TYPE·SENSITIVE: 회원 코드 중 하나라도 규칙 목록에 있으면 PASS. */
    private ConditionTrace codes(Rule rule, Set<String> userCodes, boolean sensitive) {
        if (userCodes == null) {
            return unknown(rule, describe(rule), "미입력",
                    sensitive ? UnknownReason.SENSITIVE_UNKNOWN : UnknownReason.MISSING_VALUE, false);
        }
        boolean pass = userCodes.stream().anyMatch(rule.values()::contains);
        String actual = sensitive ? HIDDEN : (userCodes.isEmpty() ? "해당 없음" : String.join(", ", userCodes));
        return result(rule, describe(rule), actual, pass);
    }

    /**
     * D-48: 지원조건 코드 그룹. 규칙 값 = 그룹에서 Y인 코드 목록 (전부 Y·Y 없음·해당사항없음 Y는 규칙을 만들지 않는다).
     * 일반 항목으로 해당하면 PASS. 아니고 Y 목록에 있는 항목 중 알 수 없는 것이 있으면 UNKNOWN. 모두 알고도 없으면 FAIL.
     */
    private ConditionTrace jaGroup(ProfileSnapshot p, Rule rule, String group) {
        Set<String> wanted = new HashSet<>(rule.values());
        String expected = group + " " + String.join(", ", rule.values());
        return switch (group) {
            case "JA03" -> traitGroup(rule, expected, wanted, p.statusCodes(), p.sensitiveCodes(), JA03_SENSITIVE, null);
            case "JA04" -> traitGroup(rule, expected, wanted, p.householdTypeCodes(), p.sensitiveCodes(), JA04_SENSITIVE,
                    p.hasHouse());
            case "JA11", "JA12" -> smallBusiness(rule, expected, p.statusCodes());
            default -> unknown(rule, expected, "-", UnknownReason.UNSUPPORTED_RULE, false);
        };
    }

    private ConditionTrace traitGroup(Rule rule, String expected, Set<String> wanted, Set<String> general,
                                      Set<String> sensitive, Set<String> sensitiveCodes, Boolean hasHouse) {
        Set<String> known = new HashSet<>();
        if (general != null) {
            known.addAll(general);
        }
        if (sensitive != null) {
            known.addAll(sensitive);
        }
        if (Boolean.FALSE.equals(hasHouse)) {
            known.add(NO_HOUSE_CODE);
        }
        if (known.stream().anyMatch(wanted::contains)) {
            return result(rule, expected, HIDDEN, true);
        }
        // 해당하는 코드를 못 찾았을 때, 알 수 없는 출처에 Y 코드가 걸려 있으면 UNKNOWN
        boolean generalUnknown = general == null && wanted.stream().anyMatch(c -> !sensitiveCodes.contains(c) && !NO_HOUSE_CODE.equals(c));
        boolean houseUnknown = hasHouse == null && wanted.contains(NO_HOUSE_CODE);
        boolean sensitiveUnknown = sensitive == null && wanted.stream().anyMatch(sensitiveCodes::contains);
        if (generalUnknown || houseUnknown) {
            return unknown(rule, expected, "미입력", UnknownReason.MISSING_VALUE, false);
        }
        if (sensitiveUnknown) {
            return unknown(rule, expected, HIDDEN, UnknownReason.SENSITIVE_UNKNOWN, false);
        }
        return result(rule, expected, HIDDEN, false);
    }

    /** D-49: 소상공인 조건은 자영업 회원만 조건부, 나머지는 해당 없음. 업종은 묻지 않는다. */
    private ConditionTrace smallBusiness(Rule rule, String expected, Set<String> status) {
        if (status == null) {
            return unknown(rule, expected, "미입력", UnknownReason.MISSING_VALUE, false);
        }
        if (status.contains(ProfileSnapshot.SELF_EMPLOYED)) {
            return unknown(rule, expected, "자영업", UnknownReason.NEEDS_CHECK, false);
        }
        return result(rule, expected, "자영업 아님", false);
    }

    // ---------- 공통 비교 ----------

    private ConditionTrace compareText(Rule rule, String actual, boolean hidden) {
        if (actual == null) {
            return unknown(rule, describe(rule), "미입력", UnknownReason.MISSING_VALUE, false);
        }
        boolean pass = switch (rule.operator()) {
            case EQ -> rule.values().size() == 1 && rule.values().get(0).equalsIgnoreCase(actual);
            case IN -> rule.values().stream().anyMatch(v -> v.equalsIgnoreCase(actual));
            default -> throw new IllegalArgumentException("문자 비교에 쓸 수 없는 연산자: " + rule.operator());
        };
        return result(rule, describe(rule), hidden ? HIDDEN : actual, pass);
    }

    private ConditionTrace compareNumber(Rule rule, BigDecimal actual) {
        if (actual == null) {
            return unknown(rule, describe(rule), "미입력", UnknownReason.MISSING_VALUE, false);
        }
        return result(rule, describe(rule), actual.stripTrailingZeros().toPlainString(), numberMatches(rule, actual));
    }

    static boolean numberMatches(Rule rule, BigDecimal actual) {
        List<String> v = rule.values();
        return switch (rule.operator()) {
            case EQ -> single(v).compareTo(actual) == 0;
            case LTE -> actual.compareTo(single(v)) <= 0;
            case GTE -> actual.compareTo(single(v)) >= 0;
            case BETWEEN -> {
                if (v.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN 값은 2개여야 함: " + v);
                }
                yield actual.compareTo(new BigDecimal(v.get(0))) >= 0 && actual.compareTo(new BigDecimal(v.get(1))) <= 0;
            }
            case IN -> v.stream().anyMatch(x -> new BigDecimal(x).compareTo(actual) == 0);
        };
    }

    private static BigDecimal single(List<String> values) {
        if (values.size() != 1) {
            throw new IllegalArgumentException("값이 1개여야 함: " + values);
        }
        return new BigDecimal(values.get(0));
    }

    private static BigDecimal toDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static String describe(Rule rule) {
        List<String> v = rule.values();
        return switch (rule.operator()) {
            case EQ -> v.isEmpty() ? "" : v.get(0);
            case IN -> String.join(", ", v);
            case LTE -> v.isEmpty() ? "" : v.get(0) + " 이하";
            case GTE -> v.isEmpty() ? "" : v.get(0) + " 이상";
            case BETWEEN -> v.size() == 2 ? v.get(0) + "~" + v.get(1) : String.join("~", v);
        };
    }

    private static ConditionTrace result(Rule rule, String expected, String actual, boolean pass) {
        return new ConditionTrace(rule.id(), rule.field(), expected, actual, pass ? Outcome.PASS : Outcome.FAIL,
                null, rule.sourceQuote(), false);
    }

    private static ConditionTrace unknown(Rule rule, String expected, String actual, UnknownReason reason, boolean boundary) {
        return new ConditionTrace(rule.id(), rule.field(), expected, actual, Outcome.UNKNOWN, reason,
                rule.sourceQuote(), boundary);
    }
}
