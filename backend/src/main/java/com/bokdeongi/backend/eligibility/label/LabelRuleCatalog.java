package com.bokdeongi.backend.eligibility.label;

import com.bokdeongi.backend.eligibility.Operator;
import com.bokdeongi.backend.eligibility.Rule;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사람이 검수한 판정 조건 라벨 (D-93). 파일은 data-lab/rule_labels/export_label_rules.py가 만드는
 * {@code eligibility/label-rules.csv}다. 한 줄 = 조건 하나 (칸 규칙은 data-lab/rule_labels/README.md).
 *
 * <ul>
 *   <li>일반 줄 → 수동 규칙(source = MANUAL)</li>
 *   <li>UNPARSED → benefits.unparsed_conditions (그 묶음은 최대 조건부, D-64)</li>
 *   <li>NONE → 조건 없음 (규칙 0개)</li>
 *   <li>OUT_OF_SCOPE → 시설·기관 대상, 개인 추천 제외 (target_scope = CORP, D-87)</li>
 *   <li>question 칸 → 추가 질문 문구 (ANSWER:{key}의 key별, BEN-04 화면)</li>
 * </ul>
 */
public final class LabelRuleCatalog {

    public static final String RESOURCE = "eligibility/label-rules.csv";
    public static final String SOURCE = "MANUAL";

    /** 규칙으로 못 바꾼 조건 원문 하나 */
    public record Unparsed(String sourceQuote, int ruleGroup) {
    }

    /**
     * 라벨이 있는 서비스 하나.
     *
     * @param labeledOn  라벨을 만든 원문의 수정일(한국 날짜). 지금 원문의 수정일과 다르면 라벨이 낡은 것
     * @param outOfScope 시설·기관 대상이라 개인 추천에서 뺀다
     */
    public record LabeledService(String serviceId, LocalDate labeledOn, List<Rule> rules, List<Unparsed> unparsed,
                                 boolean outOfScope) {
        public LabeledService {
            rules = List.copyOf(rules);
            unparsed = List.copyOf(unparsed);
        }

        /** 넣은 뒤의 benefits.rules_status */
        public String rulesStatus() {
            return unparsed.isEmpty() ? "DONE" : "PARTIAL";
        }
    }

    private static final LabelRuleCatalog EMPTY = new LabelRuleCatalog(Map.of(), Map.of());
    private static volatile LabelRuleCatalog standard;

    private final Map<String, LabeledService> services;
    private final Map<String, String> questions;

    private LabelRuleCatalog(Map<String, LabeledService> services, Map<String, String> questions) {
        this.services = Collections.unmodifiableMap(services);
        this.questions = Collections.unmodifiableMap(questions);
    }

    /** 앱에 들어 있는 라벨 파일. 없으면 빈 목록 */
    public static LabelRuleCatalog standard() {
        LabelRuleCatalog c = standard;
        if (c == null) {
            synchronized (LabelRuleCatalog.class) {
                c = standard;
                if (c == null) {
                    c = load();
                    standard = c;
                }
            }
        }
        return c;
    }

    static LabelRuleCatalog load() {
        try (InputStream in = LabelRuleCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return EMPTY;
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LabelRuleCatalog parse(String csv) {
        Map<String, LocalDate> labeledOn = new LinkedHashMap<>();
        Map<String, List<Rule>> rules = new LinkedHashMap<>();
        Map<String, List<Unparsed>> unparsed = new LinkedHashMap<>();
        Map<String, Boolean> outOfScope = new LinkedHashMap<>();
        Map<String, String> questions = new LinkedHashMap<>();
        for (Map<String, String> row : CsvText.parse(csv)) {
            String id = row.getOrDefault("service_id", "").strip();
            if (id.isEmpty()) {
                continue;
            }
            labeledOn.putIfAbsent(id, LocalDate.parse(row.get("labeled_on").strip()));
            rules.putIfAbsent(id, new ArrayList<>());
            unparsed.putIfAbsent(id, new ArrayList<>());
            outOfScope.putIfAbsent(id, false);
            String field = row.get("field").strip();
            int group = Integer.parseInt(row.get("group").strip());
            String quote = row.getOrDefault("source_quote", "").strip();
            switch (field) {
                case "NONE" -> {
                    // 조건 없음
                }
                case "OUT_OF_SCOPE" -> outOfScope.put(id, true);
                case Rule.UNPARSED -> unparsed.get(id).add(new Unparsed(quote, group));
                default -> {
                    List<String> values = Arrays.stream(row.get("values").split(";")).map(String::strip)
                            .filter(v -> !v.isEmpty()).toList();
                    rules.get(id).add(new Rule(null, field, Operator.valueOf(row.get("operator").strip()), values,
                            SOURCE, quote, null, group));
                    String question = row.getOrDefault("question", "").strip();
                    if (field.startsWith("ANSWER:") && !question.isEmpty()) {
                        questions.putIfAbsent(field.substring("ANSWER:".length()), question);
                    }
                }
            }
        }
        Map<String, LabeledService> services = new LinkedHashMap<>();
        labeledOn.forEach((id, on) -> services.put(id,
                new LabeledService(id, on, rules.get(id), unparsed.get(id), outOfScope.get(id))));
        return new LabelRuleCatalog(services, questions);
    }

    public Map<String, LabeledService> services() {
        return services;
    }

    /** 추가 질문 key → 사람이 다듬은 질문 문구 */
    public Optional<String> question(String key) {
        return Optional.ofNullable(questions.get(key));
    }
}
