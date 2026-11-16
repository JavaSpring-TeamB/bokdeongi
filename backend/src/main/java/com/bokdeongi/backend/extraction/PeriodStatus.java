package com.bokdeongi.backend.extraction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신청 기간으로 본 공고 상태 (D-85). 동기화(DATA-01) 때마다 다시 계산해 PAST_*면 benefits.status = EXPIRED.
 *
 * <p>규칙은 data-lab/api_survey/stale_check.py와 같다. 두 구현은 같은 원문에 같은 결과를 내야 한다
 * (PeriodStatusTest가 Python 테스트와 같은 사례를 쓴다).
 */
public final class PeriodStatus {

    public enum Label {
        /** 신청 기간이 작년 이전에 끝남 */
        PAST_OLD,
        /** 올해 신청 기간이 이미 끝났고 반복 표시·다음 기간이 없음 */
        PAST_THIS,
        /** '매년' 등 반복 표시 → 다음 접수 예상 시기로 안내 */
        RECURRING,
        /** 진행 중이거나 앞으로 있음 */
        OPEN,
        /** 상시·기관별 상이·신청 불필요·'○○일로부터 N년 이내'·날짜 없음 */
        NO_DATE;

        public boolean expired() {
            return this == PAST_OLD || this == PAST_THIS;
        }
    }

    /**
     * @param label     분류
     * @param staleText 보조 표시: 지원대상 원문의 연도가 모두 작년 이전 (자동 제외하지 않음)
     * @param lastDate  읽은 날짜 중 가장 늦은 날 (없으면 null)
     */
    public record Result(Label label, boolean staleText, LocalDate lastDate) {
    }

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern RECURRING = Pattern.compile("매년|해마다|매월|매분기|분기별|매 ?학기|연\\s*\\d+\\s*회", FLAGS);
    private static final Set<String> NO_DATE_TYPES = Set.of("상시신청", "접수기관별상이");
    private static final Pattern NO_DATE_TEXT =
            Pattern.compile("상시|수시|연중|신청\\s*불필요|신청\\s*절차(가)?\\s*없|별도\\s*신청\\s*없|상이", FLAGS);
    /** 사람마다 기준일이 다른 기간. 괄호 속 연도는 법 시행일 등이라 마감이 아니다 */
    private static final Pattern RELATIVE =
            Pattern.compile("(로부터|부터)\\s*\\d+\\s*(일|주|개월|년)|\\d+\\s*(일|주|개월|년)\\s*이내", FLAGS);
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*(?:년|\\.|-|/)", FLAGS);

    private PeriodStatus() {
    }

    /**
     * @param periodType   신청기한유형 (상시신청, 접수기관별상이, 직접입력 …)
     * @param periodText   신청기한 원문
     * @param targetText   지원대상 원문 (보조 표시용)
     * @param modifiedYear 수정일시의 연도. 연도 없는 날짜를 채울 때만 쓴다
     */
    public static Result classify(String periodType, String periodText, String targetText, Integer modifiedYear,
            LocalDate today) {
        String text = periodText == null ? "" : periodText;
        List<Integer> targetYears = years(targetText);
        boolean staleText = !targetYears.isEmpty() && max(targetYears) < today.getYear();

        if (RECURRING.matcher(text).find()) {
            return new Result(Label.RECURRING, staleText, null);
        }
        if (NO_DATE_TYPES.contains(periodType) || NO_DATE_TEXT.matcher(text).find() || RELATIVE.matcher(text).find()) {
            return new Result(Label.NO_DATE, staleText, null);
        }
        // 연도가 적힌 날짜만 믿는다. 연도 표기가 하나도 없으면 수정 연도로 추정하지 않는다
        List<Integer> explicitYears = years(text);
        List<LocalDate> dates = dates(text, explicitYears.isEmpty() ? null : modifiedYear);
        if (dates.isEmpty()) {
            if (!explicitYears.isEmpty() && max(explicitYears) < today.getYear()) {
                return new Result(Label.PAST_OLD, staleText, null);   // '2025년 상반기'
            }
            if (!explicitYears.isEmpty() && max(explicitYears) == today.getYear() && halfPassed(text, today)) {
                return new Result(Label.PAST_THIS, staleText, null);  // '2026년 상반기' (7월 이후)
            }
            return new Result(Label.NO_DATE, staleText, null);
        }
        LocalDate last = dates.stream().max(LocalDate::compareTo).orElseThrow();
        if (!last.isBefore(today)) {
            return new Result(Label.OPEN, staleText, last);
        }
        return new Result(last.getYear() < today.getYear() ? Label.PAST_OLD : Label.PAST_THIS, staleText, last);
    }

    /** 원문 날짜를 날짜로. 일이 없으면 그달 말일, 연도를 모르면 버린다. */
    static List<LocalDate> dates(String text, Integer baseYear) {
        List<LocalDate> out = new ArrayList<>();
        for (DateMention m : ExtractionValidator.extractDates(text, baseYear)) {
            if (m.year() == null || m.month() < 1 || m.month() > 12) {
                continue;
            }
            YearMonth ym = YearMonth.of(m.year(), m.month());
            int day = m.day() == null ? ym.lengthOfMonth() : Math.min(m.day(), ym.lengthOfMonth());
            if (day < 1) {
                continue;
            }
            out.add(ym.atDay(day));
        }
        return out;
    }

    static List<Integer> years(String text) {
        List<Integer> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = YEAR.matcher(text);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    private static boolean halfPassed(String text, LocalDate today) {
        return text.contains("상반기") && !text.contains("하반기") && today.getMonthValue() >= 7;
    }

    private static int max(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }
}
