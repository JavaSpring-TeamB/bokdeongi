package com.bokdeongi.backend.eligibility.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 판정에 쓰는 JSON 칸(값 목록·추가 질문 답·판정 근거)만 다루는 작은 읽기·쓰기.
 *
 * <p>JSON 라이브러리를 쓰지 않는 이유: 이 프로젝트에는 Jackson 3(Spring Boot 4)과 Jackson 2(jjwt)가 함께 있어
 * 버전마다 패키지가 다르다. 판정 데이터는 평평한 배열·객체뿐이라 직접 다루는 쪽이 버전에 묶이지 않는다.
 * 지원하는 값: 문자열, 숫자, true/false/null, 배열, 객체.
 */
public final class JsonText {

    private JsonText() {
    }

    // ---------------------------------------------------------------- 읽기

    /** 값 하나를 읽는다. 문자열은 String, 숫자는 원문 그대로의 String, true/false는 Boolean, null은 null. */
    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Reader r = new Reader(json);
        Object value = r.value();
        r.skipSpaces();
        if (r.pos != json.length()) {
            throw new IllegalArgumentException("JSON 뒤에 남은 글자: " + json.substring(r.pos));
        }
        return value;
    }

    /** ["A","B"] 또는 [19, 34] → 글자 목록. null·빈 칸이면 null (D-45: 건너뜀과 빈 목록을 구분). */
    public static List<String> stringList(String json) {
        Object v = parse(json);
        if (v == null) {
            return null;
        }
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON 배열이 아님: " + json);
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }

    /** {"key":"Y"} → 글자 맵. 값이 문자열이 아니면 글자로 바꾼다. null·빈 칸이면 빈 맵. */
    public static Map<String, String> stringMap(String json) {
        Object v = parse(json);
        Map<String, String> out = new LinkedHashMap<>();
        if (v == null) {
            return out;
        }
        if (!(v instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON 객체가 아님: " + json);
        }
        map.forEach((k, val) -> {
            if (val != null) {
                out.put(k.toString(), val.toString());
            }
        });
        return out;
    }

    /** [{"sourceQuote":..,"ruleGroup":..}] → 객체 목록. null·빈 칸이면 빈 목록. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objectList(String json) {
        Object v = parse(json);
        List<Map<String, Object>> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        if (!(v instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON 배열이 아님: " + json);
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) {
            this.s = s;
        }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            skipSpaces();
            if (pos >= s.length()) {
                throw new IllegalArgumentException("JSON이 끝남");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '"' -> string();
                case '[' -> array();
                case '{' -> object();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String word, Object result) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("잘못된 JSON: " + s.substring(pos));
            }
            pos += word.length();
            return result;
        }

        private String number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("잘못된 JSON: " + s.substring(pos));
            }
            return s.substring(start, pos);
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            pos++; // 여는 따옴표
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> sb.append(e); // \" \\ \/
                }
            }
            throw new IllegalArgumentException("닫히지 않은 문자열");
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            pos++;
            skipSpaces();
            if (s.charAt(pos) == ']') {
                pos++;
                return out;
            }
            while (true) {
                out.add(value());
                skipSpaces();
                char c = s.charAt(pos++);
                if (c == ']') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("배열에 ',' 또는 ']'가 와야 함");
                }
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            pos++;
            skipSpaces();
            if (s.charAt(pos) == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipSpaces();
                if (s.charAt(pos) != '"') {
                    throw new IllegalArgumentException("객체 키는 문자열이어야 함");
                }
                String key = string();
                skipSpaces();
                if (s.charAt(pos++) != ':') {
                    throw new IllegalArgumentException("객체에 ':'가 와야 함");
                }
                out.put(key, value());
                skipSpaces();
                char c = s.charAt(pos++);
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("객체에 ',' 또는 '}'가 와야 함");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 쓰기

    /** 글자 목록 → ["A","B"] */
    public static String writeStrings(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            quote(sb, values.get(i));
        }
        return sb.append(']').toString();
    }

    /** 키 순서를 지키는 객체 하나를 쓴다. 값: String, Number, Boolean, null */
    public static void writeObject(StringBuilder sb, Map<String, ?> fields) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                quote(sb, v.toString());
            }
        }
        sb.append('}');
    }

    static void quote(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
