package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.extraction.Spec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM이 낸 조건 한 줄의 필드·연산자·값이 엔진이 읽을 수 있는 모양인지 보고 표준형으로 맞춘다 (DATA-03 저장 전 검증 3번).
 *
 * <p>목록은 data-lab/conditions/prompt_c5_system.txt의 [필드]와 같다. 프롬프트 필드를 바꾸면 여기도 바꾼다.
 */
public final class ConditionFields {

    public static final String UNPARSED = "UNPARSED";
    public static final String NONE = "NONE";
    public static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";
    public static final String ANSWER_PREFIX = "ANSWER:";

    /** 코드 필드: 값이 하나여도 IN (프롬프트 연산자 규칙). */
    static final Map<String, Set<String>> CODE_VALUES = Map.of(
            "HOUSEHOLD_TYPE", Set.of("JA0403", "JA0404", "JA0411", "JA0413", "JA0414"),
            "STATUS", Set.of("JA0313", "JA0314", "JA0315", "JA0316", "JA0317", "JA0318", "JA0319", "JA0320",
                    "JA0326", "JA0327", "SELF_EMPLOYED"),
            "SENSITIVE", Set.of("JA0301", "JA0302", "JA0303", "JA0328", "JA0329", "JA0330", "JA0401", "JA0402"),
            "HOUSING", Set.of("OWN", "JEONSE", "MONTHLY", "ETC"),
            "WELFARE", Set.of("BASIC", "NEAR_POOR"));

    /** 값이 정해진 글자인 필드: EQ 또는 IN. */
    static final Map<String, Set<String>> TEXT_VALUES = Map.of(
            "GENDER", Set.of("M", "F"),
            "HAS_HOUSE", Set.of("true", "false"),
            "MARITAL", Set.of("SINGLE", "MARRIED", "ETC"));

    /** 숫자 필드. INCOME_PCT만 소수를 허용한다. */
    static final Set<String> NUMERIC = Set.of("AGE", "INCOME_PCT", "HOUSEHOLD_SIZE", "CHILDREN_COUNT",
            "YOUNGEST_CHILD_AGE");

    /** benefit_rules.field는 60자 (v2.11). "ANSWER:" 7자를 빼면 key는 53자까지 */
    private static final Pattern ANSWER_KEY = Pattern.compile("[a-z][a-z0-9_]{0,52}");

    private ConditionFields() {
    }

    /** 판정 규칙이 아닌 특수 줄 (UNPARSED·NONE·OUT_OF_SCOPE). */
    public static boolean special(String field) {
        String f = field == null ? "" : field.strip();
        return UNPARSED.equals(f) || NONE.equals(f) || OUT_OF_SCOPE.equals(f);
    }

    /** 검사 결과: 통과하면 표준형 spec, 아니면 error. */
    public record Checked(Spec spec, String error) {

        static Checked ok(String field, String operator, List<String> values) {
            return new Checked(new Spec(field, operator, values), null);
        }

        static Checked fail(String error) {
            return new Checked(null, error);
        }

        public boolean ok() {
            return spec != null;
        }
    }

    /** 필드·연산자·값을 검사하고 표준형(대소문자, 코드 필드 IN, 숫자 표기)으로 맞춘다. */
    public static Checked check(String rawField, String rawOperator, List<String> rawValues) {
        String field = rawField == null ? "" : rawField.strip();
        String op = rawOperator == null ? "" : rawOperator.strip().toUpperCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        if (rawValues != null) {
            for (String v : rawValues) {
                if (v != null && !v.isBlank()) {
                    values.add(v.strip());
                }
            }
        }
        if (field.isEmpty()) {
            return Checked.fail("필드가 비어 있음");
        }
        if (values.isEmpty()) {
            return Checked.fail(field + ": 값이 비어 있음");
        }

        if (field.startsWith(ANSWER_PREFIX)) {
            String key = field.substring(ANSWER_PREFIX.length());
            if (!ANSWER_KEY.matcher(key).matches()) {
                return Checked.fail("ANSWER key 모양이 아님: " + key);
            }
            String answer = values.get(0).toUpperCase(Locale.ROOT);
            if (values.size() != 1 || !("Y".equals(answer) || "N".equals(answer))) {
                return Checked.fail(field + ": 값은 Y 또는 N 하나");
            }
            return Checked.ok(field, "EQ", List.of(answer));
        }

        Set<String> codes = CODE_VALUES.get(field);
        if (codes != null) {
            if (!("IN".equals(op) || "EQ".equals(op))) {
                return Checked.fail(field + ": 코드 필드에 쓸 수 없는 연산자 " + op);
            }
            Set<String> out = new LinkedHashSet<>();
            for (String v : values) {
                String code = v.toUpperCase(Locale.ROOT);
                if (!codes.contains(code)) {
                    return Checked.fail(field + ": 목록에 없는 코드 " + v);
                }
                out.add(code);
            }
            return Checked.ok(field, "IN", List.copyOf(out));
        }

        Set<String> texts = TEXT_VALUES.get(field);
        if (texts != null) {
            if (!("IN".equals(op) || "EQ".equals(op))) {
                return Checked.fail(field + ": 쓸 수 없는 연산자 " + op);
            }
            Set<String> out = new LinkedHashSet<>();
            for (String v : values) {
                String t = "HAS_HOUSE".equals(field) ? v.toLowerCase(Locale.ROOT) : v.toUpperCase(Locale.ROOT);
                if (!texts.contains(t)) {
                    return Checked.fail(field + ": 목록에 없는 값 " + v);
                }
                out.add(t);
            }
            return Checked.ok(field, out.size() == 1 ? "EQ" : "IN", List.copyOf(out));
        }

        if (NUMERIC.contains(field)) {
            int need = switch (op) {
                case "EQ", "LTE", "GTE" -> 1;
                case "BETWEEN" -> 2;
                default -> -1;
            };
            if (need < 0) {
                return Checked.fail(field + ": 숫자 필드에 쓸 수 없는 연산자 " + op);
            }
            if (values.size() != need) {
                return Checked.fail(field + " " + op + ": 값 개수가 " + need + "개가 아님");
            }
            List<String> out = new ArrayList<>();
            for (String v : values) {
                BigDecimal n;
                try {
                    n = new BigDecimal(v.replace(",", ""));
                } catch (NumberFormatException e) {
                    return Checked.fail(field + ": 숫자가 아님 " + v);
                }
                boolean integral = n.stripTrailingZeros().scale() <= 0;
                if (n.signum() < 0 || (!"INCOME_PCT".equals(field) && !integral)) {
                    return Checked.fail(field + ": 쓸 수 없는 숫자 " + v);
                }
                out.add(n.stripTrailingZeros().toPlainString());
            }
            if ("BETWEEN".equals(op) && new BigDecimal(out.get(0)).compareTo(new BigDecimal(out.get(1))) > 0) {
                out = List.of(out.get(1), out.get(0));
            }
            return Checked.ok(field, op, out);
        }

        if ("REGION".equals(field)) {
            if (!("IN".equals(op) || "EQ".equals(op))) {
                return Checked.fail("REGION: 쓸 수 없는 연산자 " + op);
            }
            return Checked.ok(field, "IN", List.copyOf(new LinkedHashSet<>(values)));
        }

        return Checked.fail("목록에 없는 필드: " + field);
    }

    /** 근거 문구에 있어야 하는 숫자 (숫자 필드만). */
    static List<String> numbers(Spec spec) {
        return NUMERIC.contains(spec.field()) ? spec.values() : List.of();
    }
}
