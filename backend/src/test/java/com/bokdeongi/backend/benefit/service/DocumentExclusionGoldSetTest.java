package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** DATA-06·07: 정답셋으로 서류 목록(평균 F1)과 중복 불가 문구(있는지 없는지) 정확도를 잰다. LLM 호출 없음 */
class DocumentExclusionGoldSetTest {

    private static final Path GOLD_DIR = Path.of("..", "data-lab", "gold_set");
    /** 첫 버전: 서류 F1 0.92, 중복 불가 30/30 */
    private static final double MIN_DOC_F1 = 0.85;
    private static final double MIN_EXCLUSION = 0.9;

    @Test
    void 정답셋_서류와_중복불가() throws Exception {
        assumeTrue(Files.exists(GOLD_DIR.resolve("labels.csv")), "정답셋 파일이 없어 건너뜀");
        Map<String, Map<String, String>> samples = new HashMap<>();
        for (Map<String, String> s : GoldSetCsv.read(GOLD_DIR.resolve("samples.csv"))) {
            samples.put(s.get("service_id"), s);
        }
        List<Map<String, String>> labels = GoldSetCsv.read(GOLD_DIR.resolve("labels.csv"));

        double f1Sum = 0;
        int exclusionHit = 0;
        StringBuilder report = new StringBuilder("정답셋 서류·중복 불가\n");
        for (Map<String, String> label : labels) {
            Map<String, String> s = samples.get(label.get("service_id"));
            Set<String> expected = names(List.of(label.getOrDefault("self_docs", "").split(";")));
            Set<String> actual = names(DocumentRuleExtractor.extract(s.get("required_docs_text")));
            double f1 = f1(expected, actual);
            f1Sum += f1;
            if (f1 < 1) {
                report.append(String.format("  서류 %.2f %s: 정답 %s / 규칙 %s%n", f1, label.get("title"), expected, actual));
            }
            String exclusion = ExclusionRuleExtractor.extract(s.get("target_text"), s.get("criteria_text"), s.get("content_text"));
            if (exclusion.isEmpty() == label.getOrDefault("exclusion_text", "").isBlank()) {
                exclusionHit++;
            } else {
                report.append("  중복 불가 틀림 - ").append(label.get("title")).append(": 규칙 [").append(exclusion).append("]\n");
            }
        }
        double docF1 = f1Sum / labels.size();
        double exclusionRate = (double) exclusionHit / labels.size();
        report.append(String.format("서류 평균 F1 %.2f, 중복 불가 %d/%d%n", docF1, exclusionHit, labels.size()));
        System.out.println(report);
        assertTrue(docF1 >= MIN_DOC_F1, "서류 F1 미달\n" + report);
        assertTrue(exclusionRate >= MIN_EXCLUSION, "중복 불가 정확도 미달\n" + report);
    }

    /** "통장 사본" = "통장사본": 띄어쓰기·가운뎃점 차이는 무시 */
    private static Set<String> names(List<String> items) {
        Set<String> set = new HashSet<>();
        for (String item : items) {
            String key = item.replaceAll("[\\s·ㆍ.]", "");
            if (!key.isEmpty()) {
                set.add(key);
            }
        }
        return set;
    }

    private static double f1(Set<String> expected, Set<String> actual) {
        if (expected.isEmpty() && actual.isEmpty()) {
            return 1;
        }
        Set<String> common = new HashSet<>(expected);
        common.retainAll(actual);
        if (common.isEmpty()) {
            return 0;
        }
        double p = (double) common.size() / actual.size();
        double r = (double) common.size() / expected.size();
        return 2 * p * r / (p + r);
    }
}
