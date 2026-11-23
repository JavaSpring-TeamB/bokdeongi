package com.bokdeongi.backend.eligibility;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 판정 trace의 요구 조건·내 값(기계 값)을 화면에 보여 줄 말로 바꾼다. DB·Spring에 의존하지 않는다.
 * <p>
 * trace에는 판정에 쓴 값을 그대로 남기고(JA0201, MONTHLY, 19 이상), 화면으로 내보낼 때만 이 클래스로 바꾼다.
 * 그래서 이미 저장된 판정 결과도 다시 판정하지 않고 바로 고쳐 보인다.
 */
public final class ConditionText {

    private static final Pattern GROUP_PREFIX = Pattern.compile("^JA\\d{2}\\s+");
    private static final Pattern CODE = Pattern.compile("\\bJA\\d{4}\\b");
    private static final Pattern WORD = Pattern.compile("\\b[A-Za-z_]+\\b");
    /** 단위를 아직 안 붙인 숫자 (뒤에 숫자·점·%가 오지 않는 것) */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)(?![\\d.%])");

    private static final Map<String, String> CODE_EXTRA = Map.of(ProfileSnapshot.SELF_EMPLOYED, "자영업자");

    /** 필드별 기계 값 → 말. 필드마다 따로 두어 같은 글자(M, ETC)가 다른 뜻으로 섞이지 않게 한다 */
    private static final Map<String, Map<String, String>> WORDS = Map.of(
            "GENDER", Map.of("M", "남성", "F", "여성"),
            "HOUSING", Map.of("OWN", "자가", "JEONSE", "전세", "MONTHLY", "월세", "ETC", "기타"),
            "HAS_HOUSE", Map.of("true", "주택 있음", "false", "무주택"),
            "MARITAL", Map.of("SINGLE", "미혼", "MARRIED", "기혼", "ETC", "기타"),
            "WELFARE", Map.of("BASIC", "기초생활수급자", "NEAR_POOR", "차상위계층", "NONE", "해당 없음",
                    "UNKNOWN", "모름"),
            "ANSWER", Map.of("Y", "예", "N", "아니요", "UNSURE", "모름"));

    private ConditionText() {
    }

    /** 공고가 요구하는 값. 예) JA_GROUP:JA04 "JA04 JA0412" → "무주택세대", AGE "19 이상" → "만 19세 이상" */
    public static String expected(String field, String raw) {
        if (raw == null || field == null) {
            return raw;
        }
        String base = field.startsWith("JA_GROUP:") ? GROUP_PREFIX.matcher(raw).replaceFirst("") : raw;
        String text = words(field, codes(base));
        if (!text.equals(base) || !NUMBER.matcher(text).find()) {
            return text;
        }
        return switch (field) {
            case "AGE" -> "만 " + unit(text, "세");
            case "INCOME_PCT" -> "중위소득 " + unit(text, "%");
            case "HOUSEHOLD_SIZE" -> unit(text, "명");
            case "CHILDREN_COUNT" -> "자녀 " + unit(text, "명");
            case "YOUNGEST_CHILD_AGE" -> "막내 " + unit(text, "세");
            default -> text;
        };
    }

    /** 회원 값. 예) AGE "35" → "만 35세", STATUS "JA0327" → "구직자·실업자". 미입력·비공개는 그대로 */
    public static String actual(String field, String raw) {
        if (raw == null || field == null) {
            return raw;
        }
        String text = words(field, codes(raw));
        if (!text.equals(raw) || !NUMBER.matcher(text).find()) {
            return text;
        }
        return switch (field) {
            case "AGE" -> "만 " + unit(text, "세");
            case "HOUSEHOLD_SIZE", "CHILDREN_COUNT" -> unit(text, "명");
            case "YOUNGEST_CHILD_AGE" -> unit(text, "세");
            default -> text;
        };
    }

    private static String codes(String text) {
        Matcher m = CODE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    SupportConditionConverter.NAMES.getOrDefault(m.group(), m.group())));
        }
        m.appendTail(out);
        String result = out.toString();
        for (Map.Entry<String, String> e : CODE_EXTRA.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private static String words(String field, String text) {
        Map<String, String> dict = WORDS.get(field.startsWith("ANSWER:") ? "ANSWER" : field);
        if (dict == null) {
            return text;
        }
        Matcher m = WORD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(dict.getOrDefault(m.group(), m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** "19~34" → "19~34세", "2 이상" → "2명 이상". 범위는 끝 숫자에만 단위를 붙인다 */
    private static String unit(String text, String unit) {
        Matcher m = NUMBER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int end = m.end();
            boolean rangeStart = end < text.length() && text.charAt(end) == '~';
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + (rangeStart ? "" : unit)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
