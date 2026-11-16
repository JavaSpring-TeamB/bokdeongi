package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.extraction.ExtractionValidator;

/**
 * DATA-05: data-lab/gold_set 정답셋으로 금액 규칙 추출 정확도를 잰다.
 * LLM을 부르지 않으므로 CI에서 매번 돈다.
 */
class AmountGoldSetTest {

    private static final Path GOLD_DIR = Path.of("..", "data-lab", "gold_set");
    private static final double MIN_ACCURACY = 0.9;

    @Test
    void 정답셋_금액_정확도() throws Exception {
        assumeTrue(Files.exists(GOLD_DIR.resolve("labels.csv")), "정답셋 파일이 없어 건너뜀");
        Map<String, Map<String, String>> labels = new HashMap<>();
        for (Map<String, String> label : GoldSetCsv.read(GOLD_DIR.resolve("labels.csv"))) {
            labels.put(label.get("service_id"), label);
        }
        List<Map<String, String>> samples = GoldSetCsv.read(GOLD_DIR.resolve("samples.csv"));

        Map<String, Integer> hits = new LinkedHashMap<>(Map.of("max_amount", 0, "amount_period", 0, "annual_amount", 0));
        StringBuilder report = new StringBuilder("정답셋 금액\n");
        for (Map<String, String> s : samples) {
            Map<String, String> label = labels.get(s.get("service_id"));
            AmountRuleExtractor.Result r = AmountRuleExtractor.extract(
                    s.get("content_text"), s.get("support_type"), s.get("title"));
            if (r.maxAmount() != null) { // 숫자 읽기 기준은 검증 모듈 하나
                assertTrue(ExtractionValidator.extractNumbers(s.get("content_text")).contains(ExtractionValidator.canon(r.maxAmount().toString())),
                        s.get("title") + ": 검증 모듈이 읽지 못한 금액 " + r.maxAmount());
            }
            Map<String, String> got = Map.of(
                    "max_amount", r.maxAmount() == null ? "" : r.maxAmount().toString(),
                    "amount_period", r.period() == null ? "" : r.period().name(),
                    "annual_amount", r.annualAmount() == null ? "" : r.annualAmount().toString());
            for (String field : hits.keySet()) {
                String expected = label.getOrDefault(field, "").trim();
                if (expected.equals(got.get(field))) {
                    hits.merge(field, 1, Integer::sum);
                } else {
                    report.append("  틀림 - ").append(s.get("title")).append(" / ").append(field)
                            .append(": 정답 [").append(expected).append("] 규칙 [").append(got.get(field)).append("]\n");
                }
            }
        }
        hits.forEach((f, h) -> report.append("  ").append(f).append(": ").append(h).append('/').append(samples.size()).append('\n'));
        System.out.println(report);
        hits.forEach((f, h) -> assertTrue((double) h / samples.size() >= MIN_ACCURACY, f + " 정확도 미달\n" + report));
    }
}
