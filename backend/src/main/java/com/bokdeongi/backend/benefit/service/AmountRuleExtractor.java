package com.bokdeongi.backend.benefit.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 지원 금액을 규칙(정규식)으로 뽑는다. LLM을 부르지 않는다. (DATA-05)
 *
 * 숫자 읽는 방식은 B의 검증 모듈(data-lab/validation/validator.py extract_numbers, cases.json numbers)과 같다.
 * "20만원" → 200000, "200천원" → 200000, "19,250원" → 19250, "1억 2천만원" → 120000000.
 *
 * 1. 지원내용에서 "원"으로 끝나는 금액을 모두 찾는다
 * 2. 금액이 아닌 것은 뺀다: 단가(ha당·톤당·/톤, 같은 줄 앞에 "ha당"), 사업비·예산, 조건 기준(3만원 이상·1억원 이하),
 *    이용자가 내는 돈(본인부담금·자부담, 그 줄에 지원·감면이 없을 때)
 * 3. 남은 것 중 가장 큰 값이 max_amount. 대상별로 다르면("초 20만원, 중고 30만원") 큰 쪽
 * 4. 금액 바로 앞에 "월", "매월"이 있으면 MONTH, "연", "연간"이 있으면 YEAR, 아니면 ONCE
 *    범위("매월 300천원~500천원")면 범위 앞을 본다
 * 5. 연간 금액: MONTH ×12, YEAR 그대로, ONCE × 연 횟수("연 2회", "일년에 두 번", "설·추석"). 보험·융자는 비움
 * 6. 현물은 "지원금액"처럼 금액이라고 적힌 줄이 있을 때만 적는다
 */
public final class AmountRuleExtractor {

    public enum Period { MONTH, YEAR, ONCE }

    /** maxAmount가 null이면 금액 없음. quote는 고른 금액이 있는 원문 한 줄 */
    public record Result(Long maxAmount, Period period, Long annualAmount, String quote, String rule) {
        static Result none(String rule) {
            return new Result(null, null, null, "", rule);
        }
    }

    /** 원문 속 금액 하나 */
    record Money(long value, int start, int end, String line) {
    }

    // ---- 숫자 읽기 (validator.py와 같은 규칙)
    private static final Map<String, Long> UNITS = Map.of(
            "억", 100_000_000L, "천만", 10_000_000L, "백만", 1_000_000L,
            "십만", 100_000L, "만", 10_000L, "천", 1_000L, "백", 100L);
    private static final Pattern YMD = Pattern.compile(
            "(?<![\\d.])((?:19|20)\\d{2})\\s*[.\\-/]\\s*(\\d{1,2})(?:\\s*[.\\-/]\\s*(\\d{1,2}))?(?!\\d)");
    private static final Pattern MD = Pattern.compile("(?<![\\d.,])(\\d{1,2})(?:\\.\\s*(\\d{1,2})\\.|-(\\d{1,2}))(?!\\d)");
    /** 쉼표는 세 자리 묶음일 때만 숫자 일부로 본다 ("1,2순위"는 1과 2) */
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?\\s*(억|천만|백만|십만|만|천|백)?");

    // ---- 금액 고르기
    /** 금액 앞뒤에 오면 단가라서 뺀다 */
    private static final Pattern UNIT_PRICE_BEFORE = Pattern.compile("(ha|헥타르|톤|kg|㎡|m2|평|두|마리|주|개|시간|회|km)\\s*당\\s*$");
    private static final Pattern UNIT_PRICE_AFTER = Pattern.compile("^\\s*(/\\s*(ha|헥타르|톤|kg|㎡|m2|평|두|마리|주|개|시간|회|km)|당\\b|per)");
    /** 같은 줄에서 금액보다 앞에 단가 기준이 나오면 그 뒤 금액은 단가다: "ha당 논 25만원, 채소 55만원" */
    private static final Pattern UNIT_PRICE_IN_LINE = Pattern.compile("(ha|헥타르|톤|kg|㎡|m2|평|두|마리)\\s*당");
    /** 이용자가 내는 돈. 같은 줄에 지원·감면 같은 말이 없을 때만 뺀다 */
    private static final Pattern USER_PAYS = Pattern.compile("본인\\s*부담|자부담|이용자\\s*부담|이용료|수수료");
    private static final Pattern SUPPORT_WORD = Pattern.compile("지원|감면|경감|환급|면제|보조");
    /** 금액 앞의 범위 시작 부분: "300천원~", "30만~" */
    private static final Pattern RANGE_HEAD = Pattern.compile("[\\d,.]+\\s*(억|천만|백만|십만|만|천|백)?\\s*원?\\s*[~∼〜]\\s*$");
    /** 이 말이 있는 줄의 금액은 지원금이 아니다 */
    private static final Pattern NOT_SUPPORT_LINE = Pattern.compile("총\\s*사업비|사업비|사업\\s*규모|예산|국비|지방비|도비|시비|군비|기금");
    /** 금액 바로 앞에 오면 자격 기준(매출·소득·재산·가격)이라서 뺀다 */
    private static final Pattern THRESHOLD_BEFORE = Pattern.compile("(매출액?|소득|재산|자산|부채|가격|금액이|부부합산)[^\\d\\n]{0,8}$");
    /** 금액 바로 뒤에 오면 조건 기준이라서 뺀다 */
    private static final Pattern THRESHOLD_AFTER = Pattern.compile("^\\s*(이상|이하|미만|초과)");
    private static final Pattern MONTHLY_BEFORE = Pattern.compile("(월|매월|매달|월간)\\s*(최대|최고|평균)?\\s*$");
    private static final Pattern YEARLY_BEFORE = Pattern.compile("(연|연간|년간|매년|1년)\\s*(최대|최고)?\\s*$");
    private static final Pattern TIMES = Pattern.compile("연\\s*(\\d)\\s*회|년\\s*(\\d)\\s*회");
    private static final Pattern TWICE_WORDS = Pattern.compile("일년에\\s*두\\s*번|연\\s*두\\s*번|설\\s*[,·및/]\\s*추석|설날\\s*[,·및/]\\s*추석");
    private static final Pattern AMOUNT_LABEL = Pattern.compile("지원\\s*금액|지원액|지급액|금액\\s*:|지원\\s*한도|한도");

    private AmountRuleExtractor() {
    }

    public static Result extract(String contentText, String supportType) {
        return extract(contentText, supportType, "");
    }

    /** title: 서비스명. "안전보험"처럼 보험 상품이면 연간 금액을 비운다 */
    public static Result extract(String contentText, String supportType, String title) {
        String text = contentText == null ? "" : contentText;
        String type = supportType == null ? "" : supportType;
        List<Money> candidates = new ArrayList<>();
        for (Money money : moneyMentions(text)) {
            String before = text.substring(Math.max(0, money.start() - 12), money.start());
            String rest = text.substring(money.end()).replaceFirst("^\\s*원", "");
            String after = rest.substring(0, Math.min(rest.length(), 8));
            if (UNIT_PRICE_BEFORE.matcher(before).find() || UNIT_PRICE_AFTER.matcher(after).find()) {
                continue;
            }
            if (THRESHOLD_AFTER.matcher(after).find() || THRESHOLD_BEFORE.matcher(before).find()
                    || NOT_SUPPORT_LINE.matcher(money.line()).find()) {
                continue;
            }
            String lineBefore = text.substring(text.lastIndexOf('\n', money.start() - 1) + 1, money.start());
            if (UNIT_PRICE_IN_LINE.matcher(lineBefore).find()) {
                continue;
            }
            if (USER_PAYS.matcher(money.line()).find() && !SUPPORT_WORD.matcher(money.line()).find()) {
                continue;
            }
            candidates.add(money);
        }
        if (candidates.isEmpty()) {
            return Result.none("금액 없음");
        }
        boolean inKind = type.contains("현물") && !type.contains("현금");
        if (inKind) {
            candidates.removeIf(m -> !AMOUNT_LABEL.matcher(m.line()).find());
            if (candidates.isEmpty()) {
                return Result.none("현물(금액 표시 없음)");
            }
        }

        Money best = candidates.get(0);
        for (Money m : candidates) {
            if (m.value() > best.value()) {
                best = m;
            }
        }
        String before = text.substring(Math.max(0, best.start() - 30), best.start());
        before = RANGE_HEAD.matcher(before).replaceFirst(""); // "매월 300천원~500천원"은 "매월"을 본다
        before = before.substring(Math.max(0, before.length() - 8));
        Period period = MONTHLY_BEFORE.matcher(before).find() ? Period.MONTH
                : YEARLY_BEFORE.matcher(before).find() ? Period.YEAR : Period.ONCE;

        Long annual;
        if (type.contains("보험") || type.contains("융자") || (title != null && title.contains("보험"))) {
            annual = null; // 사고 보험금·대출 한도는 1년 단위로 따지지 않는다
        } else if (period == Period.MONTH) {
            annual = best.value() * 12;
        } else if (period == Period.YEAR) {
            annual = best.value();
        } else {
            annual = best.value() * timesPerYear(best.line(), text);
        }
        return new Result(best.value(), period, annual, best.line(), "규칙");
    }

    /** 지급 횟수가 아닌 활동 횟수가 적힌 줄: "교육 연 2회", "방문 상담 연 4회" */
    private static final Pattern ACTIVITY_LINE = Pattern.compile("교육|상담|점검|방문|모임|회의|행사|강좌|프로그램|평가");

    /**
     * "연 2회", "일년에 두 번", "설·추석" → 2. 없으면 1.
     * 금액이 있는 줄을 먼저 보고, 없으면 교육·상담 같은 활동 횟수 줄은 빼고 공고 전체에서 찾는다.
     */
    static int timesPerYear(String amountLine, String text) {
        Integer inLine = times(amountLine);
        if (inLine != null) {
            return inLine;
        }
        for (String line : text.split("\n")) {
            if (ACTIVITY_LINE.matcher(line).find()) {
                continue;
            }
            Integer t = times(line);
            if (t != null) {
                return t;
            }
        }
        return 1;
    }

    private static Integer times(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = TIMES.matcher(line);
        if (m.find()) {
            return Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return TWICE_WORDS.matcher(line).find() ? 2 : null;
    }

    /** "원"으로 끝나는 금액. "1억 2천만원"처럼 단위가 이어지면 합친다 */
    static List<Money> moneyMentions(String text) {
        List<Money> out = new ArrayList<>();
        // "7천5백만원" → "7500 만원"처럼 만 아래 자릿수 묶음을 숫자 하나로 바꾼 뒤 읽는다 (글자 수는 그대로)
        String masked = mergeSubGroups(maskDates(text));
        List<MatchResult> ms = new ArrayList<>();
        Matcher m = NUMBER.matcher(masked);
        while (m.find()) {
            ms.add(new MatchResult(m.start(), m.end(), m.group(1), m.group(2), m.group(3)));
        }
        for (int i = 0; i < ms.size(); i++) {
            MatchResult first = ms.get(i);
            long total = value(first);
            int end = first.end();
            int j = i;
            // 더 작은 단위가 공백만 두고 이어지면 합친다
            while (j + 1 < ms.size() && first.unit() != null) {
                MatchResult cur = ms.get(j);
                MatchResult nxt = ms.get(j + 1);
                if (!masked.substring(cur.end(), nxt.start()).isBlank() || nxt.unit() == null
                        || UNITS.get(nxt.unit()) >= UNITS.get(cur.unit())) {
                    break;
                }
                total += value(nxt);
                end = nxt.end();
                j++;
            }
            if (masked.substring(end).stripLeading().startsWith("원")) {
                out.add(new Money(total, first.start(), end, lineAt(text, first.start())));
                i = j;
            }
        }
        return out;
    }

    /**
     * 만 아래 자릿수가 띄어쓰기 없이 이어진 묶음: "7천5백", "1천2백3십4" (validator.py _SUB_GROUP과 같음).
     * 숫자 하나로 바꾸면 뒤의 만·억은 NUMBER가 묶음 전체에 곱한다 ("7천5백만" = 7500만).
     */
    private static final Pattern SUB_GROUP = Pattern.compile("(?<![\\d.,])(?:\\d+[천백십])+(?:\\d+(?=[만억]))?");
    private static final Pattern SUB_PART = Pattern.compile("(\\d+)([천백십])");
    private static final Map<String, Long> SUB_SCALE = Map.of("천", 1000L, "백", 100L, "십", 10L);

    /**
     * "7천5백" → "7500"처럼 바꾼다. 자릿수가 하나뿐("5천", "200천")이거나 천→백→십 순서가 아니면 그대로 둔다.
     * 원문 위치(인용 줄 찾기)가 어긋나지 않게 바꾼 숫자 뒤를 공백으로 채워 글자 수를 유지한다.
     */
    static String mergeSubGroups(String text) {
        Matcher g = SUB_GROUP.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (g.find()) {
            String run = g.group();
            Matcher p = SUB_PART.matcher(run);
            List<Long> scales = new ArrayList<>();
            long total = 0;
            int last = 0;
            while (p.find()) {
                long scale = SUB_SCALE.get(p.group(2));
                scales.add(scale);
                total += Long.parseLong(p.group(1)) * scale;
                last = p.end();
            }
            String tail = run.substring(last);
            boolean descending = true;
            for (int k = 1; k < scales.size(); k++) {
                descending &= scales.get(k) < scales.get(k - 1);
            }
            String merged = run;
            if (scales.size() + (tail.isEmpty() ? 0 : 1) >= 2 && descending) {
                if (!tail.isEmpty()) {
                    total += Long.parseLong(tail);
                }
                String digits = Long.toString(total);
                if (digits.length() <= run.length()) {
                    merged = digits + " ".repeat(run.length() - digits.length());
                }
            }
            g.appendReplacement(sb, Matcher.quoteReplacement(merged));
        }
        g.appendTail(sb);
        return sb.toString();
    }

    private record MatchResult(int start, int end, String digits, String fraction, String unit) {
    }

    private static long value(MatchResult m) {
        BigDecimal number = canon(m.digits() + (m.fraction() == null ? "" : m.fraction()));
        if (m.unit() != null) {
            number = number.multiply(BigDecimal.valueOf(UNITS.get(m.unit())));
        }
        return number.longValue();
    }

    /** 날짜(2026.04.27, 01-31)는 금액으로 읽지 않게 같은 길이의 공백으로 바꾼다 */
    private static String maskDates(String text) {
        StringBuilder sb = new StringBuilder(text);
        for (Pattern p : List.of(YMD, MD)) {
            Matcher m = p.matcher(sb.toString());
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) {
                    sb.setCharAt(i, ' ');
                }
            }
        }
        return sb.toString();
    }

    private static String lineAt(String text, int index) {
        int s = text.lastIndexOf('\n', index) + 1;
        int e = text.indexOf('\n', index);
        String line = text.substring(s, e < 0 ? text.length() : e).strip();
        return line.replaceFirst("^[\\-○ㅇ·•※▶◦\\s]+", "");
    }

    private static BigDecimal canon(String value) {
        return strip(new BigDecimal(value.replace(",", "")));
    }

    private static BigDecimal strip(BigDecimal number) {
        BigDecimal s = number.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}
