package com.bokdeongi.backend.sync;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 공고 변경 감지 (DATA-08). 원문이 바뀐 혜택에서 사용자에게 의미 있는 칸만 비교해 요약을 만든다. DB·Spring에 의존하지 않는다.
 *
 * <ul>
 *   <li>비교하는 칸: 신청 기간, 지원 대상·선정기준, 지원 금액, 지원 내용, 구비서류, 신청 방법, 공고 이름</li>
 *   <li>조회수·동기화 시각·가공 신뢰도 같은 사소한 칸은 보지 않는다</li>
 *   <li>글은 띄어쓰기·줄바꿈 차이를 무시하고 비교한다</li>
 *   <li>요약은 가장 중요한 변경 하나 + 나머지 개수 ("신청기한이 변경됐어요 (외 2건)")</li>
 * </ul>
 * 결과는 benefits.last_change_summary·last_changed_fields·last_changed_at에 저장하고, 관심·진행 중 사용자 화면에 보여 준다.
 */
public final class ChangeDetector {

    /** 비교 전 값 (benefits에 저장돼 있던 것) */
    public record Before(String title, String applyPeriodText, LocalDate applyStart, LocalDate applyEnd,
                         String dateStatus, String targetText, String criteriaText, Integer maxAmount,
                         String amountPeriod, String contentText, String requiredDocsText, String applyMethodText,
                         String onlineApplyUrl) {
    }

    /** 바뀐 칸 하나. before·after는 화면에 보여 줄 만큼만 (긴 글은 앞 200자) */
    public record Change(String field, String label, String before, String after) {
    }

    /** @param summary 요약 문장, 변경이 없으면 null */
    public record Result(String summary, List<Change> changes) {
        public boolean changed() {
            return !changes.isEmpty();
        }
    }

    static final int TEXT_LIMIT = 200;

    /** 우선순위 순서. 요약 문장은 앞에 있는 것으로 만든다 */
    private static final Map<String, String> SUMMARY = new LinkedHashMap<>();
    static {
        SUMMARY.put("APPLY_PERIOD", "신청기한이 변경됐어요");
        SUMMARY.put("TARGET", "지원 대상·조건이 변경됐어요");
        SUMMARY.put("AMOUNT", "지원 금액이 변경됐어요");
        SUMMARY.put("CONTENT", "지원 내용이 변경됐어요");
        SUMMARY.put("DOCUMENTS", "제출 서류가 변경됐어요");
        SUMMARY.put("APPLY_METHOD", "신청 방법이 변경됐어요");
        SUMMARY.put("TITLE", "공고 이름이 변경됐어요");
    }

    private ChangeDetector() {
    }

    public static Result diff(Before b, BenefitRow a) {
        List<Change> changes = new ArrayList<>();
        // 신청 기간: 읽어 낸 날짜가 바뀌었거나, 원문 문구가 바뀌었으면
        if (!Objects.equals(b.applyStart(), a.applyStart()) || !Objects.equals(b.applyEnd(), a.applyEnd())
                || !Objects.equals(b.dateStatus(), a.dateStatus()) || textChanged(b.applyPeriodText(), a.applyPeriodText())) {
            changes.add(new Change("APPLY_PERIOD", "신청 기간", period(b.applyStart(), b.applyEnd(), b.applyPeriodText()),
                    period(a.applyStart(), a.applyEnd(), a.applyPeriodText())));
        }
        if (textChanged(b.targetText(), a.targetText()) || textChanged(b.criteriaText(), a.criteriaText())) {
            changes.add(new Change("TARGET", "지원 대상·조건", cut(join(b.targetText(), b.criteriaText())),
                    cut(join(a.targetText(), a.criteriaText()))));
        }
        boolean amountChanged = !Objects.equals(b.maxAmount(), a.maxAmount())
                || !Objects.equals(b.amountPeriod(), a.amountPeriod());
        if (amountChanged) {
            changes.add(new Change("AMOUNT", "지원 금액", amount(b.maxAmount(), b.amountPeriod()),
                    amount(a.maxAmount(), a.amountPeriod())));
        }
        if (!amountChanged && textChanged(b.contentText(), a.contentText())) {
            changes.add(new Change("CONTENT", "지원 내용", cut(b.contentText()), cut(a.contentText())));
        }
        if (textChanged(b.requiredDocsText(), a.requiredDocsText())) {
            changes.add(new Change("DOCUMENTS", "제출 서류", cut(b.requiredDocsText()), cut(a.requiredDocsText())));
        }
        if (textChanged(b.applyMethodText(), a.applyMethodText()) || textChanged(b.onlineApplyUrl(), a.onlineApplyUrl())) {
            changes.add(new Change("APPLY_METHOD", "신청 방법", cut(b.applyMethodText()), cut(a.applyMethodText())));
        }
        if (textChanged(b.title(), a.title())) {
            changes.add(new Change("TITLE", "공고 이름", b.title(), a.title()));
        }
        if (changes.isEmpty()) {
            return new Result(null, List.of());
        }
        String first = SUMMARY.keySet().stream().filter(k -> changes.stream().anyMatch(c -> c.field().equals(k)))
                .findFirst().orElseThrow();
        String summary = SUMMARY.get(first) + (changes.size() > 1 ? " (외 " + (changes.size() - 1) + "건)" : "");
        return new Result(summary, List.copyOf(changes));
    }

    static boolean textChanged(String before, String after) {
        return !norm(before).equals(norm(after));
    }

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private static String period(LocalDate start, LocalDate end, String text) {
        if (start == null && end == null) {
            return cut(text);
        }
        return (start == null ? "" : start.toString()) + " ~ " + (end == null ? "" : end.toString());
    }

    private static String amount(Integer value, String period) {
        if (value == null) {
            return null;
        }
        String unit = switch (period == null ? "" : period) {
            case "MONTH" -> "월 ";
            case "YEAR" -> "연 ";
            default -> "";
        };
        return unit + String.format("%,d원", value);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return b == null || b.isBlank() ? a : a + "\n" + b;
    }

    static String cut(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.length() <= TEXT_LIMIT ? t : t.substring(0, TEXT_LIMIT) + "…";
    }
}
