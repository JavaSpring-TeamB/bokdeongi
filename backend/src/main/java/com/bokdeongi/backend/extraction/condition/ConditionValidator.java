package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.eligibility.Operator;
import com.bokdeongi.backend.eligibility.Rule;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.extraction.Item;
import com.bokdeongi.backend.extraction.Purpose;
import com.bokdeongi.backend.extraction.Spec;
import com.bokdeongi.backend.extraction.Validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM이 두 번 뽑은 조건(D-60)을 검증해 저장할 규칙으로 바꾼다. DB·LLM에 의존하지 않는 순수 계산이다.
 *
 * <ul>
 *   <li>근거 문구는 그 공고 원문(서비스명·지원대상·선정기준·지원내용·신청방법)에만 대조한다 (D-59)</li>
 *   <li>신뢰도 = 숫자 0.4 + 코드 모순 없음 0.3 + 반복 일치 0.3, 확인 못 함은 절반 (D-61).
 *       엔진은 0.80 미만 LLM 규칙을 조건부로 본다</li>
 *   <li>버린 줄은 같은 묶음의 UNPARSED로 남긴다. 줄을 그냥 빼면 조건이 느슨해져 해당 없는 사람이 '해당'이 될 수 있어서다</li>
 *   <li>두 번째 결과에만 있는 조건이 있으면 첫 결과가 조건을 빠뜨렸을 수 있으므로 0번 묶음 UNPARSED (공고 전체 최대 조건부)</li>
 * </ul>
 */
public final class ConditionValidator {

    public static final String SOURCE = "LLM";
    static final String QUOTE_NOT_FOUND = "자동 추출한 조건의 근거를 원문에서 찾지 못함 (원문 확인 필요)";
    static final String REPEAT_MISMATCH = "두 번 추출한 조건이 서로 달라 원문 확인 필요";

    private ConditionValidator() {
    }

    /** 검수 파일 한 줄의 상태. */
    public enum LineStatus {
        /** 저장, 신뢰도 기준(0.80) 이상 */
        KEPT,
        /** 저장했지만 신뢰도가 낮아 판정에서 조건부 */
        LOW_CONFIDENCE,
        /** 검증 실패로 버리고 UNPARSED로 남김 */
        DROPPED,
        /** LLM이 UNPARSED로 낸 줄 */
        UNPARSED,
        /** NONE·OUT_OF_SCOPE */
        SPECIAL,
        /** 두 번째 결과에만 있던 줄 (저장 안 함, 0번 묶음 UNPARSED 추가) */
        REPEAT_ONLY
    }

    /** B가 검수할 줄. 라벨 파일(rule_labels.csv) 칸 + 검증 결과. */
    public record ReviewLine(String serviceId, int group, String field, String operator, List<String> values,
            String sourceQuote, String question, LineStatus status, BigDecimal confidence, String checks,
            String reason) {
    }

    /**
     * 공고 하나의 저장 결과.
     *
     * @param rules      benefit_rules에 넣을 LLM 규칙 (source = LLM, confidence)
     * @param unparsed   benefits.unparsed_conditions (D-64)
     * @param outOfScope 두 번 다 시설·단체 대상이면 true (target_scope = CORP)
     * @param review     검수 파일 줄
     */
    public record ServiceRules(List<Rule> rules, List<LabelRuleCatalog.Unparsed> unparsed, boolean outOfScope,
            List<ReviewLine> review) {

        public ServiceRules {
            rules = List.copyOf(rules);
            unparsed = List.copyOf(unparsed);
            review = List.copyOf(review);
        }

        public String rulesStatus() {
            return unparsed.isEmpty() ? "DONE" : "PARTIAL";
        }
    }

    public static ServiceRules validate(ConditionSource src, List<ConditionBatch.Condition> first,
            List<ConditionBatch.Condition> second) {
        List<String> sources = src.sources();
        List<Rule> rules = new ArrayList<>();
        Set<LabelRuleCatalog.Unparsed> unparsed = new LinkedHashSet<>();
        List<ReviewLine> review = new ArrayList<>();

        boolean oos1 = has(first, ConditionFields.OUT_OF_SCOPE);
        boolean oos2 = has(second, ConditionFields.OUT_OF_SCOPE);
        boolean none1 = has(first, ConditionFields.NONE);
        boolean none2 = has(second, ConditionFields.NONE);
        for (ConditionBatch.Condition c : first) {
            if (ConditionFields.OUT_OF_SCOPE.equals(field(c)) || ConditionFields.NONE.equals(field(c))) {
                review.add(line(src, c, LineStatus.SPECIAL, null, "", null));
            }
        }
        if (oos1 && oos2) {
            return new ServiceRules(List.of(), List.of(), true, review);
        }
        if (oos1 != oos2 || none1 != none2) {
            unparsed.add(new LabelRuleCatalog.Unparsed(REPEAT_MISMATCH, 0));
        }

        // 두 번째 결과의 규칙 줄 (반복 비교용, 같은 조건이 두 번 나오면 두 번 센다)
        List<Spec> repeats = new ArrayList<>();
        List<ConditionBatch.Condition> repeatLines = new ArrayList<>();
        for (ConditionBatch.Condition c : second) {
            if (!ConditionFields.special(field(c))) {
                ConditionFields.Checked checked = ConditionFields.check(c.field(), c.operator(), c.values());
                if (checked.ok()) {
                    repeats.add(checked.spec());
                    repeatLines.add(c);
                }
            }
        }

        for (ConditionBatch.Condition c : first) {
            String field = field(c);
            int group = c.groupOrZero();
            String quote = c.sourceQuote() == null ? "" : c.sourceQuote().strip();
            boolean quoteOk = !quote.isEmpty() && ExtractionValidator.quoteFound(quote, sources);
            if (ConditionFields.OUT_OF_SCOPE.equals(field) || ConditionFields.NONE.equals(field)) {
                continue;
            }
            if (ConditionFields.UNPARSED.equals(field)) {
                unparsed.add(new LabelRuleCatalog.Unparsed(quoteOk ? quote : QUOTE_NOT_FOUND, group));
                review.add(line(src, c, LineStatus.UNPARSED, null, "", quoteOk ? null : "근거 문구가 원문에 없음"));
                continue;
            }
            ConditionFields.Checked checked = ConditionFields.check(c.field(), c.operator(), c.values());
            if (!checked.ok()) {
                unparsed.add(new LabelRuleCatalog.Unparsed(quoteOk ? quote : QUOTE_NOT_FOUND, group));
                review.add(line(src, c, LineStatus.DROPPED, null, "", checked.error()));
                continue;
            }
            Spec spec = checked.spec();
            Item repeat = takeRepeat(repeats, repeatLines, spec, quote);
            Item item = new Item(spec.field(), quote, ConditionFields.numbers(spec), List.of(), spec, null, null);
            Validation v = ExtractionValidator.validate(item, Purpose.RULE, sources, codeSpec(spec, src.codeRules()),
                    repeat, src.baseYear());
            String checks = checks(v.checks());
            if (v.dropped()) {
                unparsed.add(new LabelRuleCatalog.Unparsed(quoteOk ? quote : QUOTE_NOT_FOUND, group));
                review.add(line(src, c, spec, LineStatus.DROPPED, null, checks, v.reason()));
                continue;
            }
            rules.add(new Rule(null, spec.field(), Operator.valueOf(spec.operator()), spec.values(), SOURCE, quote,
                    v.score(), group));
            LineStatus status = v.score().compareTo(new BigDecimal("0.80")) >= 0 ? LineStatus.KEPT
                    : LineStatus.LOW_CONFIDENCE;
            review.add(line(src, c, spec, status, v.score(), checks, String.join(" / ", v.notes())));
        }

        for (ConditionBatch.Condition c : repeatLines) {
            unparsed.add(new LabelRuleCatalog.Unparsed(REPEAT_MISMATCH, 0));
            review.add(line(src, c, LineStatus.REPEAT_ONLY, null, "", "두 번째 결과에만 있음"));
        }
        if (rules.isEmpty() && unparsed.isEmpty() && !(none1 && none2)) {
            // 조건이 하나도 안 남았는데 '조건 없음'이라고 두 번 답한 것도 아니면 원문 확인으로 둔다
            unparsed.add(new LabelRuleCatalog.Unparsed(QUOTE_NOT_FOUND, 0));
        }
        return new ServiceRules(rules, new ArrayList<>(unparsed), false, review);
    }

    /**
     * 같은 조건(필드·연산자·값)이 두 번째 결과에 있으면 꺼내 준다. 없으면 빈 출력 (반복 불일치).
     * 질문(ANSWER)은 실행마다 key 이름이 달라질 수 있어서(excluded_student / student_excluded) key가 달라도
     * 답(Y/N)이 같고 근거 문구가 같거나 한쪽이 다른 쪽을 포함하면 같은 조건으로 본다. 이때 첫 결과의 key를 쓴다.
     */
    private static Item takeRepeat(List<Spec> repeats, List<ConditionBatch.Condition> repeatLines, Spec spec,
            String quote) {
        int at = -1;
        for (int i = 0; i < repeats.size() && at < 0; i++) {
            if (repeats.get(i).key().equals(spec.key())) {
                at = i;
            }
        }
        if (at < 0 && spec.field().startsWith(ConditionFields.ANSWER_PREFIX)) {
            String q = ExtractionValidator.normalize(quote);
            for (int i = 0; i < repeats.size() && at < 0; i++) {
                Spec r = repeats.get(i);
                String rq = ExtractionValidator.normalize(repeatLines.get(i).sourceQuote());
                if (r.field().startsWith(ConditionFields.ANSWER_PREFIX) && r.values().equals(spec.values())
                        && !q.isEmpty() && !rq.isEmpty() && (q.contains(rq) || rq.contains(q))) {
                    at = i;
                }
            }
        }
        if (at < 0) {
            return Item.empty(spec.field());
        }
        repeats.remove(at);
        repeatLines.remove(at);
        // 반복 비교(Item.key)는 첫 결과의 조건과 같아야 일치로 센다
        return new Item(spec.field(), null, List.of(), List.of(), spec, null, null);
    }

    /**
     * 같은 필드의 코드 규칙 (D-61 모순 검사). 코드에 없는 필드면 null (확인 못 함).
     * STATUS·SENSITIVE·HOUSEHOLD_TYPE은 코드 규칙의 JA03·JA04 목록과 비교한다. 소득은 코드가 구간 코드라 비교하지 않는다.
     */
    static Spec codeSpec(Spec llm, List<Rule> codeRules) {
        String field = llm.field();
        List<String> values = new ArrayList<>();
        boolean found = false;
        for (Rule r : codeRules) {
            boolean match = switch (field) {
                case "AGE", "GENDER" -> field.equals(r.field());
                case "STATUS" -> "JA_GROUP:JA03".equals(r.field());
                case "SENSITIVE" -> "JA_GROUP:JA03".equals(r.field()) || "JA_GROUP:JA04".equals(r.field());
                case "HOUSEHOLD_TYPE" -> "JA_GROUP:JA04".equals(r.field());
                default -> false;
            };
            if (match) {
                if ("AGE".equals(field)) {
                    return new Spec(field, r.operator().name(), r.values());
                }
                found = true;
                values.addAll(r.values());
            }
        }
        return found ? new Spec(field, "IN", values) : null;
    }

    private static boolean has(List<ConditionBatch.Condition> conditions, String field) {
        return conditions.stream().anyMatch(c -> field.equals(field(c)));
    }

    private static String field(ConditionBatch.Condition c) {
        return c.field() == null ? "" : c.field().strip();
    }

    private static String checks(Map<String, Boolean> checks) {
        List<String> parts = new ArrayList<>();
        checks.forEach((k, v) -> parts.add(k + "=" + (v == null ? "-" : v ? "O" : "X")));
        return String.join(" ", parts);
    }

    private static ReviewLine line(ConditionSource src, ConditionBatch.Condition c, LineStatus status,
            BigDecimal confidence, String checks, String reason) {
        return new ReviewLine(src.serviceId(), c.groupOrZero(), field(c), c.operator() == null ? "" : c.operator(),
                c.values() == null ? List.of() : c.values(), c.sourceQuote(), c.question(), status, confidence,
                checks, reason);
    }

    private static ReviewLine line(ConditionSource src, ConditionBatch.Condition c, Spec spec, LineStatus status,
            BigDecimal confidence, String checks, String reason) {
        return new ReviewLine(src.serviceId(), c.groupOrZero(), spec.field(), spec.operator(), spec.values(),
                c.sourceQuote(), c.question(), status, confidence, checks, reason);
    }
}
