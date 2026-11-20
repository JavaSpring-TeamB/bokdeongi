package com.bokdeongi.backend.search.dto.request;

public record BenefitListRequest(
        String q,
        String hashtag,
        String sort,
        int page,
        int size
) {

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
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}