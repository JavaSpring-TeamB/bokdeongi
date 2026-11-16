package com.bokdeongi.backend.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 최소 JSON 읽기. cases.json(공용 테스트 벡터)만 읽는다.
 * Spring Boot 4가 Jackson 3으로 넘어가는 중이라 테스트가 특정 Jackson 버전에 묶이지 않게 따로 둔다.
 * 객체 → LinkedHashMap, 배열 → ArrayList, 숫자 → BigDecimal, true/false/null → Boolean/null.
 */
final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        Object value = p.value();
        p.skip();
        if (p.i != p.s.length()) {
            throw p.error("남은 글자");
        }
        return value;
    }

    private Object value() {
        skip();
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;
        skip();
        if (s.charAt(i) == '}') {
            i++;
            return map;
        }
        while (true) {
            skip();
            String key = string();
            skip();
            expect(':');
            map.put(key, value());
            skip();
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        skip();
        if (s.charAt(i) == ']') {
            i++;
            return list;
        }
        while (true) {
            list.add(value());
            skip();
            if (s.charAt(i) == ',') {
                i++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw error("모르는 이스케이프 \\" + e);
            }
        }
    }

    private BigDecimal number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw error("값이 아님");
        }
        return new BigDecimal(s.substring(start, i));
    }

    private Object literal(String word, Object value) {
        if (!s.startsWith(word, i)) {
            throw error(word + " 아님");
        }
        i += word.length();
        return value;
    }

    private void expect(char c) {
        if (s.charAt(i) != c) {
            throw error("'" + c + "' 필요");
        }
        i++;
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON " + i + "번째 글자: " + message);
    }
}
