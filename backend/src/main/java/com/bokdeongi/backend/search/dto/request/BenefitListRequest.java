package com.bokdeongi.backend.search.dto.request;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 혜택 목록 조회 조건 (GET /api/benefits).
 *
 * @param statuses 신청 상태 OPEN(신청 가능) / UPCOMING(신청 예정) / CLOSED(신청 마감). 비우면 OPEN·UPCOMING
 *                 (마감 공고는 고를 때만 보인다)
 * @param fields   분야 해시태그 이름 (GET /api/hashtags의 category = 분야). 여러 개면 '또는'
 * @param targets  대상 해시태그 이름 (category = 대상). 여러 개면 '또는'. 분야·대상·상태끼리는 '그리고'
 * @param today    신청 상태를 가를 기준일 (한국 날짜)
 */
public record BenefitListRequest(
        String q,
        String hashtag,
        String sort,
        int page,
        int size,
        Set<String> statuses,
        List<String> fields,
        List<String> targets,
        LocalDate today
) {

    public static final Set<String> STATUSES = Set.of("OPEN", "UPCOMING", "CLOSED");
    static final int MAX_TAGS = 20;
    static final int MAX_TAG_LENGTH = 30;

    /** 필터 없는 조회 (기존 호출 그대로) */
    public BenefitListRequest(String q, String hashtag, String sort, int page, int size) {
        this(q, hashtag, sort, page, size, null, null, null, null);
    }

    public BenefitListRequest {
        q = normalize(q);
        hashtag = normalize(hashtag);

        sort = sort == null || sort.isBlank()
                ? "relevance"
                : sort.trim().toLowerCase();

        if (q != null && q.length() > 100) {
            throw new IllegalArgumentException(
                    "검색어는 100글자 이하로 입력해 주세요."
            );
        }

        if (q != null && q.length() < 2) {
            throw new IllegalArgumentException(
                    "검색어는 2글자 이상 입력해 주세요."
            );
        }

        if (q != null && !q.matches("(?s).*[\\p{L}\\p{N}].*")) {
            throw new IllegalArgumentException(
                    "검색어에 문자 또는 숫자를 포함해 주세요."
            );
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "페이지 크기는 1~100 사이여야 합니다."
            );
        }

        if (!sort.equals("relevance")) {
            throw new IllegalArgumentException(
                    "지원하지 않는 정렬 방식입니다."
            );
        }

        statuses = statuses(statuses);
        fields = tags(fields, "분야");
        targets = tags(targets, "대상");
        today = today == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : today;
    }

    public boolean statusOpen() {
        return statuses.contains("OPEN");
    }

    public boolean statusUpcoming() {
        return statuses.contains("UPCOMING");
    }

    public boolean statusClosed() {
        return statuses.contains("CLOSED");
    }

    /** 분야 필터 JSON 배열 (JSON_OVERLAPS용). 고르지 않았으면 null */
    public String fieldsJson() {
        return json(fields);
    }

    public String targetsJson() {
        return json(targets);
    }

    private static Set<String> statuses(Set<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String s : raw) {
                if (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s.trim())) {
                    continue;
                }
                String v = s.trim().toUpperCase(Locale.ROOT);
                if (!STATUSES.contains(v)) {
                    throw new IllegalArgumentException("신청 상태는 OPEN / UPCOMING / CLOSED 중에서 고릅니다: " + s);
                }
                out.add(v);
            }
        }
        return out.isEmpty() ? Set.of("OPEN", "UPCOMING") : Set.copyOf(out);
    }

    private static List<String> tags(List<String> raw, String what) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            for (String t : raw) {
                String v = normalize(t);
                if (v == null || out.contains(v)) {
                    continue;
                }
                if (v.length() > MAX_TAG_LENGTH) {
                    throw new IllegalArgumentException(what + " 필터 값이 너무 깁니다: " + v);
                }
                out.add(v);
            }
        }
        if (out.size() > MAX_TAGS) {
            throw new IllegalArgumentException(what + " 필터는 " + MAX_TAGS + "개까지 고를 수 있습니다.");
        }
        return List.copyOf(out);
    }

    private static String json(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"');
            for (char ch : values.get(i).toCharArray()) {
                if (ch == '"' || ch == '\\') {
                    sb.append('\\');
                }
                if (ch < 0x20) {
                    sb.append(String.format("\\u%04x", (int) ch));
                } else {
                    sb.append(ch);
                }
            }
            sb.append('"');
        }
        return sb.append(']').toString();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
