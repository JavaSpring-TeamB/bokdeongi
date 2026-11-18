package com.bokdeongi.backend.sync;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공공서비스 하나의 API 원문 (목록 + 상세 + 지원조건). 같은 이름의 칸이 목록과 상세에 다 있으면 상세를 먼저 쓴다.
 *
 * @param list       serviceList 한 건
 * @param detail     serviceDetail 한 건 (없으면 빈 맵)
 * @param conditions supportConditions 한 건 (없으면 빈 맵)
 */
public record ServiceRecord(Map<String, Object> list, Map<String, Object> detail, Map<String, Object> conditions) {

    static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public ServiceRecord {
        list = list == null ? Map.of() : list;
        detail = detail == null ? Map.of() : detail;
        conditions = conditions == null ? Map.of() : conditions;
    }

    public String serviceId() {
        return text("서비스ID");
    }

    /** 상세 → 목록 순서로 찾는다. 빈 글자는 null */
    public String text(String... keys) {
        for (String key : keys) {
            String v = clean(detail.get(key));
            if (v != null) {
                return v;
            }
            v = clean(list.get(key));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 목록에만 있는 칸 (소관기관유형·코드, 서비스분야, 사용자구분, 조회수 등) */
    public String listText(String key) {
        return clean(list.get(key));
    }

    /**
     * 수정일시 "20260430093900"(한국 시각) → UTC. 형식이 다르면 null (격리 대상).
     * 목록과 상세가 다르면 늦은 쪽을 쓴다 (둘 중 하나만 바뀌어도 다시 가공).
     */
    public LocalDateTime modifiedAtUtc() {
        LocalDateTime a = parseTime(clean(list.get("수정일시")));
        LocalDateTime b = parseTime(clean(detail.get("수정일시")));
        if (a == null) {
            return b;
        }
        return b != null && b.isAfter(a) ? b : a;
    }

    /** 수정일시의 한국 연도 (연도 없는 날짜에 붙인다) */
    public Integer modifiedYear() {
        String raw = clean(list.get("수정일시"));
        if (raw == null) {
            raw = clean(detail.get("수정일시"));
        }
        return raw != null && raw.length() >= 4 && raw.substring(0, 4).chars().allMatch(Character::isDigit)
                ? Integer.valueOf(raw.substring(0, 4)) : null;
    }

    /** 지원조건 코드만 (JA로 시작하는 칸). 값은 글자, 빈 값은 빼지 않고 null로 둔다 */
    public Map<String, String> jaCodes() {
        Map<String, String> out = new LinkedHashMap<>();
        conditions.forEach((k, v) -> {
            if (k != null && k.startsWith("JA")) {
                out.put(k, clean(v));
            }
        });
        return out;
    }

    static LocalDateTime parseTime(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12) {
            digits = digits + "00";
        }
        if (digits.length() != 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(digits, API_TIME).atZone(KOREA).withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String clean(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().replace("\r\n", "\n").replace('\r', '\n').strip();
        return s.isEmpty() ? null : s;
    }
}
