package com.bokdeongi.backend.benefit.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신청기한을 규칙(정규식)으로 뽑는다. LLM을 부르지 않는다. (날짜 기준 v2)
 *
 * 1. 연도 + 월(초·중·말)까지만 있으면 PARSED + MONTH. 기간 판단 기준이 여러 개 겹치면 CHECK
 * 2. 연도 없이 월만 있으면 공고 게시 연도(수정일시 연도)를 붙여 PARSED
 * 3. 예산 소진 시까지, 사람마다 신청 기간이 다른 공고(상대 기간)는 CHECK
 * 4. "연초(당해년도 1월)"처럼 월이 있으면 PARSED. 단 "예정"처럼 불확실하면 CHECK
 * 5. "전년도", "해당연도"처럼 다른 해를 기준으로 하면 CHECK
 * 6. 신청 불필요는 NO_APPLICATION, API 유형 "접수기관별상이"는 VARIES (D-51)
 *    "연초"처럼 월 숫자가 없으면 CHECK, "연초(1월)"처럼 숫자가 있을 때만 PARSED (D-66)
 * 7. 그 밖의 예외는 모두 CHECK
 */
public final class ApplyPeriodExtractor {

    /** NO_APPLICATION: 신청 불필요, VARIES: API 신청기한유형이 접수기관별상이 (D-51) */
    public enum Status { PARSED, ALWAYS, NO_APPLICATION, VARIES, CHECK_NOTICE }

    public enum Precision { DAY, MONTH }

    /** starts·ends는 같은 순서로 짝을 이룬다. 날짜 형식은 "2026-03-02" 또는 "2026-03". 모르면 "" */
    public record Result(Status status, Precision precision, List<String> starts, List<String> ends,
                         boolean recurringYearly, String quote, boolean needsLlm, String rule) {
    }

    /** DB에 저장할 신청 기간 하나 */
    public record Period(String start, String end) {
    }

    // ---- 연도 있는 날짜: 2026.03.02 / 2026. 3. 2. / 2026-03-02 / 2026년 3월 2일 / '26. 2. 25.
    private static final String YEAR = "(20\\d{2}|['’‘]\\d{2})";
    private static final String Y = YEAR + "\\s*[.\\-/년]\\s*";
    private static final String MD = "(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})(?!\\d)\\s*[.일]?";
    /** 날짜 뒤 요일·시각은 건너뜀: (수) 10:00, (화) 09시 */
    private static final String TAIL = "\\s*(?:\\([^)]{0,4}\\)\\.?)?\\s*(?:\\d{1,2}(?::\\d{2}|\\s*시))?\\s*";
    private static final String TILDE = "[~∼～]";
    private static final String MONTH_WORD = "\\s*(?:말|중|초|경|중순|초순|하순)?";

    private static final Pattern DAY_RANGE = Pattern.compile(
            Y + MD + TAIL + TILDE + "\\s*(?:" + YEAR + "\\s*[.\\-/년]\\s*)?" + MD + TAIL);
    /** 2026년 6월 17일 ~ 30일 */
    private static final Pattern DAY_RANGE_SAME_MONTH = Pattern.compile(Y + MD + TAIL + TILDE + "\\s*(\\d{1,2})\\s*일");
    private static final Pattern DAY_SINGLE = Pattern.compile(Y + MD + TAIL);
    /** 2026. 4. ~ 2026. 11. / 2026. 7월 말~ 8월 말 / 2027년 2월~3월 / 2026.1. ~ 3. / 2025-12~2026-12 */
    private static final Pattern MONTH_RANGE = Pattern.compile(
            Y + "(\\d{1,2})\\s*(?:\\.|월)?(?!\\s*\\d)" + MONTH_WORD + "\\s*" + TILDE
                    + "\\s*(?:" + YEAR + "\\s*[.\\-/년]\\s*)?(\\d{1,2})\\s*(?:\\.|월)?" + MONTH_WORD);
    /** 2026년 7월 / 2026. 3월 중 */
    private static final Pattern MONTH_SINGLE = Pattern.compile(Y + "(\\d{1,2})\\s*(?:\\.|월)(?!\\s*\\d)" + MONTH_WORD);
    /** "2026년 상반기", "2026 하반기": 연도가 있는 반기는 그 반기의 첫 달~끝 달로 읽는다 (MONTH) */
    private static final Pattern HALF_YEAR = Pattern.compile("(20\\d{2})\\s*(?:년도?|\\.)?\\s*(상|하)\\s*반기");

    // ---- 연도 없는 날짜 (게시 연도를 붙인다)
    /** 01-01~01-31, 1월 2일 ~ 1월 31일 */
    private static final Pattern NY_DAY_RANGE = Pattern.compile(
            "(?<![\\d.])(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})\\s*일?\\s*" + TILDE + "\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})\\s*일?");
    /** 1월~3월, 3~5월 중, 10월~12월경 */
    private static final Pattern NY_MONTH_RANGE = Pattern.compile(
            "(?<![\\d.])(\\d{1,2})\\s*월?" + MONTH_WORD + "\\s*" + TILDE + "\\s*(\\d{1,2})\\s*월" + MONTH_WORD);
    /** 4월 말까지, 1월 */
    private static final Pattern NY_MONTH_SINGLE = Pattern.compile("(?<![\\d.~∼～])(\\d{1,2})\\s*월(?!\\s*\\d)" + MONTH_WORD);

    // ---- 문구
    private static final Pattern RECURRING = Pattern.compile("매년|해마다|매해|연초|연말");
    /** 기준 3: 사람마다 기간이 다름 / 기준 5: 다른 해 기준 */
    private static final Pattern RELATIVE = Pattern.compile(
            "(로부터|부터)\\s*\\d+\\s*(일|주|개월|년)|\\d+\\s*(일|주|개월|년)\\s*(이내|전|후|까지)|이내"
                    + "|해당\\s*연도|해당\\s*년도|전\\s*년도|전년|차\\s*년도|사업\\s*시행|사업\\s*연도|사업\\s*년도");
    /** 기준 3: 예산 소진 */
    private static final Pattern BUDGET = Pattern.compile("소진|선착순");
    /** 기준 4·6: 불확실하거나 공고·문의로 넘기는 말 */
    private static final Pattern UNCERTAIN = Pattern.compile("예정|예상|변동|미정|다를|달라|상이|다름|추후|공고문|문의|참조|참고");
    /** 기준 1: 날짜가 있어도 상시·추가 모집이나 다른 기간이 겹침 */
    private static final Pattern OVERLAP = Pattern.compile("상시|수시|추가\\s*모집|추가\\s*접수|(보험|보장|사업|운영|교육|이용)\\s*기간");
    private static final Pattern NO_APPLY = Pattern.compile(
            "신청\\s*불필요|신청\\s*불요|신청\\s*절차\\s*(가\\s*)?없|신청\\s*(이\\s*)?없|신청하지\\s*않|자동\\s*(가입|지급|선정)");
    private static final Pattern ALWAYS = Pattern.compile("상시|수시|연중");

    private ApplyPeriodExtractor() {
    }

    /**
     * 결정 ⓐ: 기간이 여러 개면 DB에는 하나만 저장한다.
     * 오늘 진행 중인 기간 → 없으면 다음에 올 기간 → 둘 다 없으면 가장 최근에 끝난 기간.
     * 월까지만 있는 날짜는 시작을 그 달 1일, 마감을 그 달 말일로 보고 비교한다. 날짜가 없으면 빈 값.
     */
    public static Optional<Period> pickPeriod(Result result, LocalDate today) {
        if (result.status() != Status.PARSED || result.starts().isEmpty()) {
            return Optional.empty();
        }
        Period ongoing = null;
        Period next = null;
        LocalDate nextStart = null;
        Period last = null;
        LocalDate lastEnd = null;
        for (int i = 0; i < result.starts().size(); i++) {
            Period p = new Period(result.starts().get(i), result.ends().get(i));
            LocalDate start = startDate(p.start());
            LocalDate end = endDate(p.end());
            if (!today.isBefore(start) && !today.isAfter(end)) {
                if (ongoing == null) {
                    ongoing = p;
                }
            } else if (start.isAfter(today)) {
                if (nextStart == null || start.isBefore(nextStart)) {
                    next = p;
                    nextStart = start;
                }
            } else if (lastEnd == null || end.isAfter(lastEnd)) {
                last = p;
                lastEnd = end;
            }
        }
        if (ongoing != null) {
            return Optional.of(ongoing);
        }
        return Optional.ofNullable(next != null ? next : last);
    }

    private static LocalDate startDate(String s) {
        if (s.isEmpty()) {
            return LocalDate.MIN;
        }
        return s.length() == 7 ? YearMonth.parse(s).atDay(1) : LocalDate.parse(s);
    }

    private static LocalDate endDate(String s) {
        if (s.isEmpty()) {
            return LocalDate.MAX;
        }
        return s.length() == 7 ? YearMonth.parse(s).atEndOfMonth() : LocalDate.parse(s);
    }

    /** 게시 연도를 모를 때. 연도 없는 날짜는 CHECK가 된다 */
    public static Result extract(String periodType, String periodText) {
        return extract(periodType, periodText, null);
    }

    /**
     * @param postedYear 공고 게시 연도 (API 수정일시의 앞 4자리). 연도 없는 날짜에 붙인다
     */
    public static Result extract(String periodType, String periodText, Integer postedYear) {
        String type = periodType == null ? "" : periodType.replace(" ", "");
        String text = periodText == null ? "" : periodText.strip();
        boolean recurring = RECURRING.matcher(text).find();

        if (type.equals("상시신청")) {
            return check(Status.ALWAYS, text, recurring, "유형:상시신청");
        }
        if (type.equals("접수기관별상이")) {
            return check(Status.VARIES, text, recurring, "유형:접수기관별상이");
        }
        if (text.isEmpty()) {
            return check(Status.CHECK_NOTICE, text, false, "원문 없음");
        }

        // 날짜가 있어도 CHECK가 되는 문구를 먼저 본다
        if (RELATIVE.matcher(text).find()) {
            return check(Status.CHECK_NOTICE, text, recurring, "상대 기간·다른 해 기준");
        }
        if (BUDGET.matcher(text).find()) {
            return check(Status.CHECK_NOTICE, text, recurring, "예산 소진·선착순");
        }
        if (UNCERTAIN.matcher(text).find()) {
            return check(Status.CHECK_NOTICE, text, recurring, "불확실(예정·상이 등)");
        }
        if (NO_APPLY.matcher(text).find()) {
            return check(Status.NO_APPLICATION, text, recurring, "신청 불필요");
        }

        Result dated = withYear(text, recurring);
        if (dated == null) {
            dated = withoutYear(text, recurring, postedYear);
        }
        if (dated == null) {
            dated = halfYear(text, recurring); // 월 날짜가 하나도 없을 때만
        }
        if (dated != null) {
            if (dated.status() == Status.PARSED && OVERLAP.matcher(text).find()) {
                return check(Status.CHECK_NOTICE, text, recurring, "기간 기준 중첩");
            }
            return dated;
        }

        if (ALWAYS.matcher(text).find()) {
            return check(Status.ALWAYS, text, recurring, "상시");
        }
        return check(Status.CHECK_NOTICE, text, recurring, "그 밖의 예외");
    }

    /** 연도가 적힌 날짜. 없으면 null */
    private static Result withYear(String text, boolean recurring) {
        List<String> starts = new ArrayList<>();
        List<String> ends = new ArrayList<>();

        Matcher m = DAY_RANGE.matcher(text);
        while (m.find()) {
            starts.add(ymd(m.group(1), m.group(2), m.group(3)));
            ends.add(ymd(m.group(4) != null ? m.group(4) : m.group(1), m.group(5), m.group(6)));
        }
        if (starts.isEmpty()) {
            m = DAY_RANGE_SAME_MONTH.matcher(text);
            while (m.find()) {
                starts.add(ymd(m.group(1), m.group(2), m.group(3)));
                ends.add(ymd(m.group(1), m.group(2), m.group(4)));
            }
        }
        if (!starts.isEmpty()) {
            return parsed(Precision.DAY, starts, ends, recurring, text, "날짜 범위");
        }

        m = MONTH_RANGE.matcher(text);
        while (m.find()) {
            starts.add(ym(m.group(1), m.group(2)));
            ends.add(ym(m.group(3) != null ? m.group(3) : m.group(1), m.group(4)));
        }
        if (!starts.isEmpty()) {
            return parsed(Precision.MONTH, starts, ends, recurring, text, "월 범위");
        }

        if (count(DAY_SINGLE, text) > 1 || count(MONTH_SINGLE, text) > 1) {
            return check(Status.CHECK_NOTICE, text, recurring, "기간 기준 중첩");
        }
        m = DAY_SINGLE.matcher(text);
        if (m.find()) {
            String date = ymd(m.group(1), m.group(2), m.group(3));
            return single(Precision.DAY, date, text.substring(m.end()), recurring, text, "날짜 하나");
        }
        m = MONTH_SINGLE.matcher(text);
        if (m.find()) {
            String month = ym(m.group(1), m.group(2));
            return single(Precision.MONTH, month, text.substring(m.end()), recurring, text, "월 하나");
        }
        return null;
    }

    /** "2026년 상반기" → 2026-01~2026-06, "2026년 하반기" → 2026-07~2026-12 (MONTH). 연도 없는 반기는 null */
    private static Result halfYear(String text, boolean recurring) {
        Matcher m = HALF_YEAR.matcher(text);
        if (!m.find()) {
            return null;
        }
        boolean first = m.group(2).equals("상");
        String year = m.group(1);
        return parsed(Precision.MONTH, new ArrayList<>(List.of(ym(year, first ? "1" : "7"))),
                new ArrayList<>(List.of(ym(year, first ? "6" : "12"))), recurring, text, "연도 있는 반기");
    }

    /** 연도 없는 날짜에 게시 연도를 붙인다. 날짜가 없으면 null */
    private static Result withoutYear(String text, boolean recurring, Integer postedYear) {
        List<String> starts = new ArrayList<>();
        List<String> ends = new ArrayList<>();
        String year = postedYear == null ? null : postedYear.toString();
        StringBuilder rest = new StringBuilder(text); // 쓰고 남은 글자에 숫자가 있으면 기준이 겹친 것

        Precision precision = null;
        Matcher m = NY_DAY_RANGE.matcher(text);
        while (m.find()) {
            precision = Precision.DAY;
            starts.add(ymd(year, m.group(1), m.group(2)));
            ends.add(ymd(year, m.group(3), m.group(4)));
            blank(rest, m);
        }
        if (precision == null) {
            m = NY_MONTH_RANGE.matcher(text);
            while (m.find()) {
                precision = Precision.MONTH;
                starts.add(ym(year, m.group(1)));
                ends.add(ym(year, m.group(2)));
                blank(rest, m);
            }
        }
        if (precision == null) {
            m = NY_MONTH_SINGLE.matcher(text);
            int count = 0;
            String after = "";
            String month = null;
            while (m.find()) {
                count++;
                month = ym(year, m.group(1));
                after = text.substring(m.end());
                blank(rest, m);
            }
            if (count > 1) {
                return check(Status.CHECK_NOTICE, text, recurring, "기간 기준 중첩");
            }
            if (count == 1) {
                if (year == null) {
                    return unsure(text, recurring, "게시 연도 모름");
                }
                if (rest.toString().matches("(?s).*\\d.*")) {
                    return check(Status.CHECK_NOTICE, text, recurring, "기간 기준 중첩");
                }
                return single(Precision.MONTH, month, after, recurring, text, "연도 없는 월 하나");
            }
            return null;
        }
        if (year == null) {
            return unsure(text, recurring, "게시 연도 모름");
        }
        if (rest.toString().matches("(?s).*\\d.*")) {
            return check(Status.CHECK_NOTICE, text, recurring, "기간 기준 중첩");
        }
        return parsed(precision, starts, ends, recurring, text, "연도 없는 날짜 + 게시 연도");
    }

    private static Result parsed(Precision precision, List<String> starts, List<String> ends,
                                 boolean recurring, String text, String rule) {
        if (starts.contains(null) || ends.contains(null)) {
            return unsure(text, recurring, "잘못된 날짜");
        }
        return new Result(Status.PARSED, precision, starts, ends, recurring, text, false, rule);
    }

    /** 날짜 하나: 뒤에 "~", "부터"가 오면 시작만, "까지"가 오면 마감만, 아니면 그 날(달) 하나 */
    private static Result single(Precision precision, String date, String after, boolean recurring,
                                 String text, String rule) {
        if (date == null) {
            return unsure(text, recurring, "잘못된 날짜");
        }
        String next = after.replaceFirst("^[\\s말중초경순하]*", "");
        boolean startOnly = next.startsWith("~") || next.startsWith("∼") || next.startsWith("부터");
        boolean endOnly = next.startsWith("까지");
        return new Result(Status.PARSED, precision,
                List.of(endOnly ? "" : date), List.of(startOnly ? "" : date), recurring, text, false, rule);
    }

    private static Result check(Status status, String text, boolean recurring, String rule) {
        return new Result(status, null, List.of(), List.of(), recurring, text, false, rule);
    }

    /** 규칙으로 정할 수 없어 사람이나 LLM이 봐야 하는 것 */
    private static Result unsure(String text, boolean recurring, String rule) {
        return new Result(Status.CHECK_NOTICE, null, List.of(), List.of(), recurring, text, true, rule);
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void blank(StringBuilder sb, Matcher m) {
        for (int i = m.start(); i < m.end(); i++) {
            sb.setCharAt(i, ' ');
        }
    }

    /** 없는 날짜(13월, 0일 등)나 연도를 모르면 null */
    private static String ymd(String year, String month, String day) {
        if (year == null) {
            return null;
        }
        int mm = Integer.parseInt(month);
        int dd = Integer.parseInt(day);
        if (mm < 1 || mm > 12 || dd < 1 || dd > 31) {
            return null;
        }
        return "%s-%02d-%02d".formatted(fullYear(year), mm, dd);
    }

    private static String ym(String year, String month) {
        if (year == null) {
            return null;
        }
        int mm = Integer.parseInt(month);
        if (mm < 1 || mm > 12) {
            return null;
        }
        return "%s-%02d".formatted(fullYear(year), mm);
    }

    /** '26 → 2026 */
    private static String fullYear(String year) {
        return year.length() == 4 ? year : "20" + year.substring(1);
    }
}
