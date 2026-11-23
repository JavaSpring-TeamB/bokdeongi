package com.bokdeongi.backend.extraction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 출력 검증 모듈 (D-57~D-60). data-lab/validation/validator.py를 옮긴 것이다 (D-58).
 *
 * <p>LLM이 공고 원문에서 뽑은 값(판정 조건, 날짜, 금액 등)을 프로그램이 직접 검사해 점수를 매긴다.
 * LLM이 스스로 말하는 확신도는 쓰지 않는다.
 * <ul>
 *   <li>근거 문구 (필수): LLM이 가리킨 근거 문구가 원문에 그대로 있는가. 없으면 버린다</li>
 *   <li>숫자 (필수): 값의 숫자·날짜가 근거 문구 안에 있는가. 다르면 버린다 (AGENTS.md 원칙 7)</li>
 *   <li>코드 (점수): 지원조건 코드로 만든 규칙과 모순되지 않는가 (RULE만)</li>
 *   <li>반복 (점수): 같은 공고를 다시 돌렸을 때 같은 값이 나오는가</li>
 * </ul>
 *
 * <p>Python판과 같은 결과를 내야 한다: 두 쪽 테스트가 같은 data-lab/validation/cases.json을 읽는다.
 * 한쪽 규칙을 바꾸면 cases.json에 사례를 추가하고 다른 쪽도 같이 고친다.
 */
public final class ExtractionValidator {

    private ExtractionValidator() {
    }

    // 점수 = 검사별 비중의 합. 통과 1, 실패 0, 확인 못 함(null) 0.5 (D-60)
    private static final Map<String, BigDecimal> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put("numbers", new BigDecimal("0.4"));
        WEIGHTS.put("code", new BigDecimal("0.3"));
        WEIGHTS.put("repeat", new BigDecimal("0.3"));
    }
    private static final BigDecimal UNCHECKED_CREDIT = new BigDecimal("0.5");
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.00");

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;

    // ---------------------------------------------------------------- 정리 (D-59)

    /** 글머리·장식 기호. 원문과 근거 문구 양쪽에서 똑같이 지운다. */
    static final String BULLETS = "○●◦•▪▫■□◆◇▶▷►※*ㅇ·ㆍ‧･∙-–—";
    private static final Map<Character, String> TRANSLATE = new HashMap<>();
    static {
        TRANSLATE.put('～', "~");
        TRANSLATE.put('〜', "~");
        TRANSLATE.put('∼', "~");
        TRANSLATE.put('‘', "'");
        TRANSLATE.put('’', "'");
        TRANSLATE.put('“', "\"");
        TRANSLATE.put('”', "\"");
        TRANSLATE.put('｢', "「");
        TRANSLATE.put('｣', "」");
        for (char ch : BULLETS.toCharArray()) {
            TRANSLATE.put(ch, "");
        }
    }
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", FLAGS);

    /** 공백·줄바꿈·글머리 기호만 지운다. 글자는 바꾸지 않는다 (비슷한 문구 허용 없음). */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String replaced = TRANSLATE.get(ch);
            out.append(replaced != null ? replaced : String.valueOf(ch));
        }
        return WHITESPACE.matcher(out).replaceAll("");
    }

    /** 근거 문구가 원문 필드 하나 안에 그대로 있는가. 여러 필드를 이어 붙여 찾지 않는다. */
    public static boolean quoteFound(String quote, List<String> sources) {
        String target = normalize(quote);
        if (target.isEmpty()) {
            return false;
        }
        for (String source : sources) {
            if (normalize(source).contains(target)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 숫자 읽기

    private static final Map<String, BigDecimal> UNITS = Map.of(
            "억", new BigDecimal("100000000"), "천만", new BigDecimal("10000000"), "백만", new BigDecimal("1000000"),
            "십만", new BigDecimal("100000"), "만", new BigDecimal("10000"), "천", new BigDecimal("1000"),
            "백", new BigDecimal("100"));

    // 날짜를 먼저 떼어 낸다. "2026.04.27"을 소수 2026.04로 읽지 않기 위해서다
    private static final Pattern YMD = Pattern.compile(
            "(?<![\\d.])((?:19|20)\\d{2})\\s*[.\\-/]\\s*(\\d{1,2})(?:\\s*[.\\-/]\\s*(\\d{1,2}))?(?!\\d)", FLAGS);
    private static final Pattern MD = Pattern.compile(
            "(?<![\\d.,])(\\d{1,2})(?:\\.\\s*(\\d{1,2})\\.|-(\\d{1,2}))(?!\\d)", FLAGS);
    // 쉼표는 세 자리 묶음일 때만 숫자 일부로 본다 ("1,2순위"는 1과 2)
    private static final Pattern NUMBER = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?\\s*(억|천만|백만|십만|만|천|백)?", FLAGS);
    // 만 아래 자릿수가 띄어쓰기 없이 이어진 묶음: "7천5백", "1천2백3십4". 뒤의 만·억은 묶음 전체에 곱한다
    private static final Pattern SUB_GROUP = Pattern.compile("(?<![\\d.,])(?:\\d+[천백십])+(?:\\d+(?=[만억]))?", FLAGS);
    private static final Pattern SUB_PART = Pattern.compile("(\\d+)([천백십])", FLAGS);
    private static final Map<String, Integer> SUB_SCALE = Map.of("천", 1000, "백", 100, "십", 10);

    /** 비교·출력용 표준형. 200000.0 → 200000, 0.50 → 0.5. 숫자가 아니면 NumberFormatException. */
    public static BigDecimal canon(String value) {
        BigDecimal number = new BigDecimal(value.trim().replace(",", ""));
        return canon(number);
    }

    static BigDecimal canon(BigDecimal number) {
        return number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
    }

    /** 숫자면 표준형 문자열, 아니면 앞뒤 공백만 지운 값. */
    static String canonText(String value) {
        try {
            return canon(value).toPlainString();
        } catch (NumberFormatException e) {
            return value.strip();
        }
    }

    /**
     * 글 속 숫자를 모두 읽는다.
     *
     * <p>"20만원"→200000, "200천원"→200000, "19,250원"→19250, "1억 2천만원"→120000000(부분 값도 포함),
     * "7천5백만원"→75000000(부분 값 없음), "60%"→60, "2025. 11. 12."→2025·11·12, "01-31"→1·31.
     * 단위가 붙은 숫자는 곱한 값만 넣는다 ("20만"에서 20은 넣지 않는다).
     */
    public static Set<BigDecimal> extractNumbers(String text) {
        Set<BigDecimal> numbers = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return numbers;
        }
        text = takeDates(YMD, text, numbers);
        text = takeDates(MD, text, numbers);
        text = mergeSubGroups(text);

        List<MatchResult> matches = NUMBER.matcher(text).results().toList();
        int i = 0;
        while (i < matches.size()) {
            MatchResult match = matches.get(i);
            BigDecimal value = canon(match.group(1) + (match.group(2) == null ? "" : match.group(2)));
            String unit = match.group(3);
            if (unit == null) {
                numbers.add(value);
                i++;
                continue;
            }
            BigDecimal total = value.multiply(UNITS.get(unit));
            numbers.add(canon(total));
            // "1억 2천만", "1만 5천"처럼 더 작은 단위가 공백만 두고 이어지면 합친 값도 넣는다
            while (i + 1 < matches.size()) {
                MatchResult next = matches.get(i + 1);
                String nextUnit = next.group(3);
                if (!text.substring(match.end(), next.start()).isBlank() || nextUnit == null
                        || UNITS.get(nextUnit).compareTo(UNITS.get(unit)) >= 0) {
                    break;
                }
                BigDecimal part = canon(next.group(1) + (next.group(2) == null ? "" : next.group(2)))
                        .multiply(UNITS.get(nextUnit));
                numbers.add(canon(part));
                total = total.add(part);
                match = next;
                unit = nextUnit;
                i++;
            }
            numbers.add(canon(total));
            i++;
        }
        return numbers;
    }

    private static String takeDates(Pattern pattern, String text, Set<BigDecimal> numbers) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) {
                    numbers.add(canon(m.group(g)));
                }
            }
            m.appendReplacement(out, " ");
        }
        m.appendTail(out);
        return out.toString();
    }

    /** 만 아래 자릿수 묶음을 숫자 하나로 바꾼다: "7천5백" → "7500". 하나뿐이거나 순서가 이상하면 그대로 둔다. */
    private static String mergeSubGroups(String text) {
        Matcher m = SUB_GROUP.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String run = m.group();
            List<int[]> parts = new ArrayList<>();   // {숫자, 자릿수}
            Matcher p = SUB_PART.matcher(run);
            while (p.find()) {
                parts.add(new int[] {Integer.parseInt(p.group(1)), SUB_SCALE.get(p.group(2))});
            }
            String tail = SUB_PART.matcher(run).replaceAll("");
            boolean descending = true;
            for (int k = 1; k < parts.size(); k++) {
                descending &= parts.get(k - 1)[1] > parts.get(k)[1];
            }
            String replacement = run;
            if (parts.size() + (tail.isEmpty() ? 0 : 1) >= 2 && descending) {
                long total = parts.stream().mapToLong(x -> (long) x[0] * x[1]).sum();
                if (!tail.isEmpty()) {
                    total += Long.parseLong(tail);
                }
                replacement = Long.toString(total);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ---------------------------------------------------------------- 날짜 읽기

    private static final Pattern DATE = Pattern.compile(
            "(?<![\\d.])(?<y1>(?:19|20)\\d{2})\\s*[.\\-/]\\s*(?<m1>\\d{1,2})(?:\\s*[.\\-/]\\s*(?<d1>\\d{1,2}))?(?!\\d)"
                    + "|(?:(?<y2>(?:19|20)\\d{2})\\s*년\\s*)?(?<m2>\\d{1,2})\\s*월(?:\\s*(?<d2>\\d{1,2})\\s*일)?"
                    + "|(?:(?<y3>(?:19|20)\\d{2})\\s*년\\s*)?(?<m5>\\d{1,2})\\s*[~〜～]\\s*(?=\\d{1,2}\\s*월)"
                    + "|~\\s*(?<m4>\\d{1,2})\\.\\s*(?<d5>\\d{1,2})(?![\\d.])"
                    + "|~\\s*(?<m6>\\d{1,2})\\.(?!\\s*\\d)"
                    + "|(?<![\\d.,])(?<m3>\\d{1,2})(?:\\.\\s*(?<d3>\\d{1,2})\\.|-(?<d4>\\d{1,2}))(?!\\d)", FLAGS);

    public static List<DateMention> extractDates(String text) {
        return extractDates(text, null);
    }

    /**
     * 글 속 날짜를 순서대로 읽는다. 연도가 없으면 앞에 나온 연도를 이어받고, 월이 줄면 다음 해로 넘긴다.
     *
     * <p>"2025. 11. 12. ~ 11. 26." → 2025-11-12, 2025-11-26 / "2026.12.1~1.31." → 2026-12-01, 2027-01-31
     *
     * @param baseYear 원문에 연도가 없을 때 쓸 공고 등록·수정 연도 (DATA-04 연도 추정 규칙). 모르면 null
     */
    public static List<DateMention> extractDates(String text, Integer baseYear) {
        List<DateMention> mentions = new ArrayList<>();
        Integer year = baseYear;
        Integer prevMonth = null;
        int prevEnd = -1;
        boolean prevHasDay = false;
        String source = text == null ? "" : text;
        Matcher m = DATE.matcher(source);
        while (m.find()) {
            // "~3. 10"은 바로 앞이 일까지 있는 날짜일 때만 날짜로 본다 ("1.5~3.5배"는 소수)
            if (m.group("m4") != null && !(prevHasDay && m.start() == prevEnd)) {
                continue;
            }
            // "~ 12."는 바로 앞(점·공백만 사이)이 월까지만 있는 날짜일 때만 날짜로 본다 ("2026.1. ~ 12.")
            if (m.group("m6") != null && !(prevEnd >= 0 && !prevHasDay
                    && source.substring(prevEnd, m.start()).replaceAll("[ .\\t]", "").isEmpty())) {
                continue;
            }
            String y = first(m, "y1", "y2", "y3");
            int month = Integer.parseInt(first(m, "m1", "m2", "m3", "m4", "m5", "m6"));
            String dayRaw = first(m, "d1", "d2", "d3", "d4", "d5");
            Integer day = dayRaw == null ? null : Integer.parseInt(dayRaw);
            if (month < 1 || month > 12 || (day != null && (day < 1 || day > 31))) {
                continue;
            }
            boolean explicit;
            if (y != null) {
                year = Integer.parseInt(y);
                explicit = true;
            } else {
                explicit = false;
                if (year != null && prevMonth != null && month < prevMonth) {
                    year += 1;
                }
            }
            mentions.add(new DateMention(year, month, day, explicit));
            prevMonth = month;
            prevEnd = m.end();
            prevHasDay = day != null;
        }
        return mentions;
    }

    private static String first(Matcher m, String... names) {
        for (String name : names) {
            if (m.group(name) != null) {
                return m.group(name);
            }
        }
        return null;
    }

    /** "2026-03-02" → [2026, 3, 2], "2026-03" → [2026, 3, null], "03-02" → [null, 3, 2]. */
    static Integer[] parseLabelDate(String value) {
        List<Integer> parts = new ArrayList<>();
        for (String piece : value.split("\\D+")) {
            if (!piece.isEmpty()) {
                parts.add(Integer.parseInt(piece));
            }
        }
        Integer year = null;
        int offset = 0;
        if (!parts.isEmpty() && parts.get(0) >= 1900) {
            year = parts.get(0);
            offset = 1;
        }
        Integer month = parts.get(offset);
        Integer day = parts.size() > offset + 1 ? parts.get(offset + 1) : null;
        return new Integer[] {year, month, day};
    }

    // ---------------------------------------------------------------- 코드와 모순 여부 (RULE)

    private static final Set<String> NUMERIC_OPERATORS = Set.of("LTE", "GTE", "BETWEEN");

    /** 숫자 조건의 허용 구간 [하한, 상한]. 상한 null = 무한. 숫자로 바꿀 수 없으면 null. */
    private static BigDecimal[] interval(Spec spec) {
        List<BigDecimal> numbers = new ArrayList<>();
        try {
            for (String v : spec.values()) {
                numbers.add(canon(v));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        String op = spec.operator();
        if ("LTE".equals(op) && numbers.size() == 1) {
            return new BigDecimal[] {BigDecimal.ZERO, numbers.get(0)};
        }
        if ("GTE".equals(op) && numbers.size() == 1) {
            return new BigDecimal[] {numbers.get(0), null};
        }
        if ("BETWEEN".equals(op) && numbers.size() == 2) {
            BigDecimal a = numbers.get(0);
            BigDecimal b = numbers.get(1);
            return new BigDecimal[] {a.min(b), a.max(b)};
        }
        if ("EQ".equals(op) && numbers.size() == 1) {
            return new BigDecimal[] {numbers.get(0), numbers.get(0)};
        }
        return null;
    }

    /**
     * LLM 조건이 코드 조건과 모순되지 않는가 (D-61).
     *
     * <p>LLM 조건이 허용하는 범위가 코드 조건 범위 안에 있으면 모순 아님 (원문이 코드보다 좁을 수는 있다).
     * 비교할 수 없는 모양(숫자 대 목록)은 모순으로 본다 (정확도 우선).
     */
    public static boolean consistent(Spec llm, Spec code) {
        if (!llm.field().equals(code.field())) {
            throw new IllegalArgumentException("다른 필드끼리 비교: " + llm.field() + " / " + code.field());
        }
        BigDecimal[] llmRange = interval(llm);
        BigDecimal[] codeRange = interval(code);
        boolean numeric = NUMERIC_OPERATORS.contains(llm.operator()) || NUMERIC_OPERATORS.contains(code.operator());
        if (llmRange != null && codeRange != null) {
            boolean lowOk = codeRange[0].compareTo(llmRange[0]) <= 0;
            boolean highOk = codeRange[1] == null || (llmRange[1] != null && llmRange[1].compareTo(codeRange[1]) <= 0);
            return lowOk && highOk;
        }
        if (numeric) {
            return false;
        }
        Set<String> llmValues = new TreeSet<>(llm.values().stream().map(ExtractionValidator::canonText).toList());
        Set<String> codeValues = new TreeSet<>(code.values().stream().map(ExtractionValidator::canonText).toList());
        return !llmValues.isEmpty() && codeValues.containsAll(llmValues);
    }

    // ---------------------------------------------------------------- 검증

    public static Validation validate(Item item, Purpose purpose, List<String> sources) {
        return validate(item, purpose, sources, null, null, null);
    }

    /**
     * LLM 출력 하나를 검증한다.
     *
     * @param sources  공고 원문 필드들 (지원대상, 선정기준, 지원내용, 신청기한 ...)
     * @param codeRule 같은 필드를 지원조건 코드로 만든 조건 (DATA-02). 없으면 null (RULE만 씀)
     * @param repeat   같은 공고를 다시 돌려 나온 같은 필드의 출력. 그 필드가 없었으면 {@link Item#empty(String)}.
     *                 안 돌렸으면 null (D-60: RULE은 항상 2회)
     * @param baseYear 공고 등록·수정 연도. 원문에 연도가 없는 날짜는 이 연도(해를 넘기면 +1)와 같아야 한다
     */
    public static Validation validate(Item item, Purpose purpose, List<String> sources, Spec codeRule, Item repeat,
            Integer baseYear) {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("quote", null);
        checks.put("numbers", null);
        checks.put("code", null);
        checks.put("repeat", null);

        if (purpose == Purpose.EASY) {
            Set<BigDecimal> sourceNumbers = new HashSet<>();
            for (String source : sources) {
                sourceNumbers.addAll(extractNumbers(source));
            }
            List<BigDecimal> missing = extractNumbers(item.text()).stream()
                    .filter(n -> !sourceNumbers.contains(n)).sorted().toList();
            checks.put("numbers", missing.isEmpty());
            if (!missing.isEmpty()) {
                return new Validation(checks, ZERO_SCORE, true,
                        "원문에 없는 숫자: " + String.join(", ", missing.stream().map(BigDecimal::toPlainString).toList()),
                        List.of());
            }
            return new Validation(checks, new BigDecimal("1.00"), false, null, List.of());
        }

        checks.put("quote", quoteFound(item.sourceQuote(), sources));
        if (!checks.get("quote")) {
            return new Validation(checks, ZERO_SCORE, true, "근거 문구가 원문에 없음", List.of());
        }

        List<String> notes = new ArrayList<>();
        Boolean numbersOk = numbersCheck(item, item.sourceQuote() == null ? "" : item.sourceQuote(), baseYear, notes);
        checks.put("numbers", numbersOk);
        if (Boolean.FALSE.equals(numbersOk)) {
            return new Validation(checks, ZERO_SCORE, true, notes.get(notes.size() - 1), notes);
        }

        if (purpose == Purpose.RULE && codeRule != null) {
            if (item.rule() == null) {
                checks.put("code", false);
                notes.add("조건 없이 코드와 비교할 수 없음");
            } else {
                checks.put("code", consistent(item.rule(), codeRule));
            }
        }
        if (repeat != null) {
            checks.put("repeat", item.key() != null && item.key().equals(repeat.key()));
        }
        return new Validation(checks, score(checks), false, null, notes);
    }

    /** 값의 숫자·날짜가 근거 문구에 있는가. 확인할 숫자가 없으면 null. */
    private static Boolean numbersCheck(Item item, String quote, Integer baseYear, List<String> notes) {
        if (item.numbers().isEmpty() && item.dates().isEmpty()) {
            return null;
        }
        Set<BigDecimal> found = extractNumbers(quote);
        for (String raw : item.numbers()) {
            BigDecimal number = canon(raw);
            if (found.contains(number)) {
                continue;
            }
            // 정수 경계 환산: "35세 미만" → 34, "2명 초과" → 3 (조건 연산자가 이하·이상뿐이라서)
            boolean integral = number.stripTrailingZeros().scale() <= 0;
            if (integral && ((quote.contains("미만") && found.contains(canon(number.add(BigDecimal.ONE))))
                    || (quote.contains("초과") && found.contains(canon(number.subtract(BigDecimal.ONE)))))) {
                notes.add("숫자 " + number.toPlainString() + "은(는) 미만·초과를 정수 경계로 바꾼 값");
                continue;
            }
            // 제외 대상 뒤집기: "만 75세 이상자 제외" → 74 이하, "18세 이하 제외" → 19 이상
            if (integral && quote.contains("제외")
                    && ((quote.contains("이상") && found.contains(canon(number.add(BigDecimal.ONE))))
                    || (quote.contains("이하") && found.contains(canon(number.subtract(BigDecimal.ONE)))))) {
                notes.add("숫자 " + number.toPlainString() + "은(는) 제외 대상의 이상·이하를 정수 경계로 바꾼 값");
                continue;
            }
            notes.add("숫자 " + number.toPlainString() + "이(가) 근거에 없음");
            return false;
        }
        List<DateMention> mentions = extractDates(quote, baseYear);
        for (String date : item.dates()) {
            Integer[] ymd = parseLabelDate(date);
            Integer year = ymd[0];
            int month = ymd[1];
            Integer day = ymd[2];
            // 월·일이 같은 날짜가 근거에 있어야 한다. 값이 월까지만이면 월만 본다
            List<DateMention> sameDay = mentions.stream()
                    .filter(x -> x.month() == month && (day == null || day.equals(x.day()))).toList();
            if (sameDay.isEmpty()) {
                notes.add("날짜 " + date + "의 월·일이 근거에 없음");
                return false;
            }
            if (year == null || sameDay.stream().anyMatch(x -> year.equals(x.year()))) {
                if (year != null && mentions.stream().noneMatch(DateMention::explicit)) {
                    notes.add("날짜 " + date + "의 연도는 게시 연도 기준 추정값");
                }
                continue;
            }
            if (sameDay.stream().allMatch(x -> x.year() == null)) {
                notes.add("날짜 " + date + "의 연도는 근거에 없어 추정값");   // DATA-04 연도 추정 규칙
                continue;
            }
            notes.add("날짜 " + date + "의 연도가 근거와 다름");
            return false;
        }
        return true;
    }

    private static BigDecimal score(Map<String, Boolean> checks) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : WEIGHTS.entrySet()) {
            Boolean ok = checks.get(e.getKey());
            BigDecimal credit = ok == null ? UNCHECKED_CREDIT : (ok ? BigDecimal.ONE : BigDecimal.ZERO);
            total = total.add(e.getValue().multiply(credit));
        }
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }
}
