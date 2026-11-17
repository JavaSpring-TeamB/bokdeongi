package com.bokdeongi.backend.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사람이 만든 정답 라벨(data-lab/rule_labels/rule_labels.csv)이 판정 엔진에서 그대로 돌아가는지.
 *
 * <p>라벨 형식(Python check_labels.py)과 엔진이 아는 필드가 어긋나면, 라벨은 통과하는데 실제 판정은 '규칙 해석 불가'가 된다.
 * 그 틈을 여기서 막는다.
 */
class LabelRulesTest {

    private static final Path LABELS = Path.of("..", "data-lab", "rule_labels", "rule_labels.csv");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    /** 규칙이 아니라 공고 단위 표시인 줄 (README 필드 목록) */
    private static final Set<String> NOT_RULES = Set.of("NONE", "OUT_OF_SCOPE");

    private final EligibilityJudge judge = new EligibilityJudge();

    /** service_id → 그 공고의 규칙. 표시용 줄(NONE·OUT_OF_SCOPE)은 규칙 없이 빈 목록. */
    static Map<String, List<Rule>> load() {
        Assumptions.assumeTrue(Files.exists(LABELS), "라벨 파일이 아직 없음(feat/B-DATA-03-eligibility-gold-labels 머지 전): " + LABELS);
        try {
            Map<String, List<Rule>> byService = new LinkedHashMap<>();
            for (Map<String, String> row : MiniCsv.parse(Files.readString(LABELS, StandardCharsets.UTF_8))) {
                List<Rule> rules = byService.computeIfAbsent(row.get("service_id"), k -> new ArrayList<>());
                rules.addAll(toRule(row));
            }
            // dev에 예전 라벨(초안·빈 양식)만 있을 때는 건너뛴다. 정답 라벨 PR이 합쳐지면 실행된다
            Assumptions.assumeTrue(byService.size() == 30 && byService.containsKey("643000000141"),
                    "정답 라벨 최종본이 아직 이 브랜치에 없음 (공고 " + byService.size() + "건)");
            return byService;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Rule> toRule(Map<String, String> row) {
        String field = row.get("field");
        int group = Integer.parseInt(row.get("group"));
        if (NOT_RULES.contains(field)) {
            return List.of();
        }
        if (Rule.UNPARSED.equals(field)) {
            return List.of(Rule.unparsed(group, row.get("source_quote")));
        }
        List<String> values = Arrays.stream(row.get("values").split(";")).map(String::strip).filter(v -> !v.isEmpty()).toList();
        return List.of(new Rule(null, field, Operator.valueOf(row.get("operator")), values, "MANUAL", row.get("source_quote"),
                null, group));
    }

    private static ProfileSnapshot filled() {
        return ProfileSnapshot.empty()
                .withBirthDate(LocalDate.of(1990, 5, 1))
                .withGender("F")
                .withRegion("서울특별시", "관악구")
                .withIncomePct(new BigDecimal("40"))
                .withHousehold(2, Set.of("JA0403"), false)
                .withStatus(Set.of("JA0313"), Set.of("JA0328"))
                .withFamily("SINGLE", 1, 2021)
                .withHousing("MONTHLY_RENT", "BASIC");
    }

    @Test
    @DisplayName("모든 라벨 줄이 엔진이 아는 필드·연산자·값 형식이다 (UNSUPPORTED_RULE 0건)")
    void everyLabelIsSupported() {
        Map<String, List<Rule>> labels = load();
        assertEquals(30, labels.size(), "표본 30건");
        List<String> unsupported = new ArrayList<>();
        for (ProfileSnapshot p : List.of(ProfileSnapshot.empty(), filled())) {
            labels.forEach((id, rules) -> judge.judge(p, rules, TODAY).trace().stream()
                    .filter(t -> t.reason() == UnknownReason.UNSUPPORTED_RULE)
                    .forEach(t -> unsupported.add(id + " " + t.field())));
        }
        assertTrue(unsupported.isEmpty(), "엔진이 해석 못 한 라벨: " + unsupported);
    }

    @Test
    @DisplayName("라벨로 끝까지 판정: 643000000141 시각장애인 가구 가스차단기 (기관 지역 충북)")
    void endToEnd() {
        List<Rule> rules = OrgRegion.apply(load().get("643000000141"), "충청북도");
        ProfileSnapshot base = ProfileSnapshot.empty().withRegion("충청북도", "청주시").withHousing(null, "BASIC");
        ProfileSnapshot yes = base.withAnswers(Map.of("visually_impaired_household", "Y"));
        ProfileSnapshot no = base.withAnswers(Map.of("visually_impaired_household", "N"));
        assertEquals(Verdict.ELIGIBLE, judge.judge(yes, rules, TODAY).verdict());
        assertEquals(Verdict.INELIGIBLE, judge.judge(no, rules, TODAY).verdict());
        assertEquals(Verdict.CONDITIONAL, judge.judge(base, rules, TODAY).verdict(), "질문에 아직 답하지 않음");
        assertEquals(Verdict.INELIGIBLE, judge.judge(yes.withRegion("부산광역시", "부산진구"), rules, TODAY).verdict());
    }
}
