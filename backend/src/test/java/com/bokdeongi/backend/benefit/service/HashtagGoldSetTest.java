package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * DATA-09: data-lab/gold_set 정답셋으로 해시태그 규칙의 평균 F1을 잰다.
 * LLM을 부르지 않으므로 CI에서 매번 돈다. 규칙을 고쳐서 점수가 기준 아래로 떨어지면 실패한다.
 */
class HashtagGoldSetTest {

    private static final Path GOLD_DIR = Path.of("..", "data-lab", "gold_set");
    /** 분야=API 기준 라벨에서 0.87. 규칙을 고치면 올린다 */
    private static final double MIN_F1 = 0.8;

    @Test
    void 정답셋_해시태그_F1() throws Exception {
        assumeTrue(Files.exists(GOLD_DIR.resolve("labels.csv")), "정답셋 파일이 없어 건너뜀");
        Map<String, String> answers = new HashMap<>();
        for (Map<String, String> label : GoldSetCsv.read(GOLD_DIR.resolve("labels.csv"))) {
            answers.put(label.get("service_id"), label.get("hashtags"));
        }

        List<Map<String, String>> samples = GoldSetCsv.read(GOLD_DIR.resolve("samples.csv"));
        double sum = 0;
        StringBuilder report = new StringBuilder("정답셋 해시태그\n");
        for (Map<String, String> s : samples) {
            List<String> got = HashtagRuleTagger.tag(new HashtagRuleTagger.Input(
                    s.get("title"), s.get("service_field"), s.get("support_type"), s.get("user_type"),
                    s.get("target_text"), s.get("content_text"),
                    new HashSet<>(Arrays.asList(s.getOrDefault("ja_codes", "").split(";"))),
                    number(s.get("age_min")), number(s.get("age_max"))));
            Set<String> expected = new HashSet<>(Arrays.asList(answers.getOrDefault(s.get("service_id"), "").split(";")));
            double f1 = f1(expected, new HashSet<>(got));
            sum += f1;
            if (f1 < 1) {
                report.append(String.format("  %.2f %s: 정답 %s / 규칙 %s%n", f1, s.get("title"), expected, got));
            }
        }
        double average = sum / samples.size();
        report.append(String.format("평균 F1 %.2f%n", average));
        System.out.println(report);
        assertTrue(average >= MIN_F1, "해시태그 평균 F1이 " + MIN_F1 + " 미만\n" + report);
    }

    private static double f1(Set<String> expected, Set<String> actual) {
        Set<String> common = new HashSet<>(expected);
        common.retainAll(actual);
        if (common.isEmpty()) {
            return 0;
        }
        double p = (double) common.size() / actual.size();
        double r = (double) common.size() / expected.size();
        return 2 * p * r / (p + r);
    }

    private static Integer number(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
