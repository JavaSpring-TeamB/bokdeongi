package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * DATA-04: data-lab/gold_set 정답셋으로 날짜 규칙 추출 정확도를 잰다.
 * LLM을 부르지 않으므로 CI에서 매번 돈다. 정확도가 기준 아래로 떨어지면 실패한다.
 */
class ApplyPeriodGoldSetTest {

    /** gradle test는 backend/ 에서 실행된다 */
    private static final Path GOLD_DIR = Path.of("..", "data-lab", "gold_set");
    private static final double MIN_ACCURACY = 0.9;

    /** 정답셋 30건 (samples.csv 원문 + labels.csv 정답) */
    @Test
    void 정답셋_날짜_정확도() throws Exception {
        assumeTrue(Files.exists(GOLD_DIR.resolve("labels.csv")), "정답셋 파일이 없어 건너뜀");
        List<Map<String, String>> samples = GoldSetCsv.read(GOLD_DIR.resolve("samples.csv"));
        List<Map<String, String>> labels = GoldSetCsv.read(GOLD_DIR.resolve("labels.csv"));
        assertAccuracy("정답셋", samples, labels);
    }

    /** 규칙을 만들 때 안 본 공고 30건 (원문과 정답이 한 파일) */
    @Test
    void 새_공고_날짜_정확도() throws Exception {
        Path holdout = GOLD_DIR.resolve("dates_holdout.csv");
        assumeTrue(Files.exists(holdout), "dates_holdout.csv가 없어 건너뜀");
        List<Map<String, String>> rows = GoldSetCsv.read(holdout);
        assertAccuracy("새 공고", rows, rows);
    }

    /** 수정일시(modified_at) 또는 posted_year의 앞 4자리 */
    private static Integer postedYear(Map<String, String> row) {
        String v = row.getOrDefault("modified_at", row.getOrDefault("posted_year", ""));
        return v.length() >= 4 && v.substring(0, 4).matches("\\d{4}") ? Integer.valueOf(v.substring(0, 4)) : null;
    }

    private static void assertAccuracy(String name, List<Map<String, String>> samples,
                                       List<Map<String, String>> labelRows) {
        Map<String, Map<String, String>> labels = new LinkedHashMap<>();
        for (Map<String, String> label : labelRows) {
            labels.put(label.get("service_id"), label);
        }

        Map<String, Function<ApplyPeriodExtractor.Result, String>> fields = new LinkedHashMap<>();
        fields.put("date_status", r -> r.status().name());
        fields.put("date_precision", r -> r.precision() == null ? "" : r.precision().name());
        fields.put("apply_start", r -> String.join(";", r.starts()));
        fields.put("apply_end", r -> String.join(";", r.ends()));
        fields.put("recurring_yearly", r -> r.recurringYearly() ? "Y" : "");

        Map<String, Integer> hits = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (Map<String, String> sample : samples) {
            Map<String, String> label = labels.get(sample.get("service_id"));
            if (label == null) {
                continue;
            }
            ApplyPeriodExtractor.Result result = ApplyPeriodExtractor.extract(
                    sample.get("period_type"), sample.get("period_text"), postedYear(sample));
            for (var field : fields.entrySet()) {
                String expected = label.getOrDefault(field.getKey(), "").trim();
                String actual = field.getValue().apply(result);
                if (expected.equalsIgnoreCase(actual)) {
                    hits.merge(field.getKey(), 1, Integer::sum);
                } else {
                    hits.putIfAbsent(field.getKey(), 0);
                    misses.add(sample.get("title") + " / " + field.getKey()
                            + ": 정답 [" + expected + "] 규칙 [" + actual + "]");
                }
            }
        }

        int total = labels.size();
        StringBuilder report = new StringBuilder(name + " 날짜 정확도 (" + total + "건)\n");
        hits.forEach((field, hit) -> report.append("  ").append(field).append(": ")
                .append(hit).append("/").append(total).append('\n'));
        misses.forEach(m -> report.append("  틀림 - ").append(m).append('\n'));
        System.out.println(report);

        hits.forEach((field, hit) -> assertTrue((double) hit / total >= MIN_ACCURACY,
                field + " 정확도가 " + MIN_ACCURACY + " 미만\n" + report));
    }
}
