package com.bokdeongi.backend.extraction.condition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * B가 검수할 파일 (CSV, UTF-8 BOM). 앞 칸은 data-lab/rule_labels/rule_labels.csv와 같고 labeled_on이 하나 더 있다.
 * 검수 후 맞는 줄만 남겨 data-lab/conditions/reviewed_rules.csv에 붙이면 export_label_rules.py가 수동 규칙(MANUAL)으로
 * 내보낸다. 그때부터 질문 문구도 그 파일 것을 쓴다 (D-93).
 */
public final class ConditionReviewFile {

    static final List<String> HEADER = List.of("service_id", "labeled_on", "group", "field", "operator", "values",
            "source_quote", "question", "labeler", "memo", "status", "confidence", "checks", "reason");

    private ConditionReviewFile() {
    }

    /**
     * @param labeledOn service_id → 원문 수정일 (한국 날짜). 라벨이 이 원문으로 만들어졌다는 표시
     */
    public static void write(Path file, List<ConditionValidator.ReviewLine> lines, Map<String, LocalDate> labeledOn) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write('﻿');
                w.write(String.join(",", HEADER));
                w.write('\n');
                for (ConditionValidator.ReviewLine l : lines) {
                    LocalDate on = labeledOn.get(l.serviceId());
                    BigDecimal c = l.confidence();
                    List<String> cells = List.of(l.serviceId(), on == null ? "" : on.toString(),
                            String.valueOf(l.group()), l.field(), l.operator(), String.join(";", l.values()),
                            nz(l.sourceQuote()), nz(l.question()), "", "", l.status().name(),
                            c == null ? "" : c.toPlainString(), nz(l.checks()), nz(l.reason()));
                    w.write(String.join(",", cells.stream().map(ConditionReviewFile::cell).toList()));
                    w.write('\n');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String cell(String s) {
        String v = s == null ? "" : s;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
