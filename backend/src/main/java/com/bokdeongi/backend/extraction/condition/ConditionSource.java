package com.bokdeongi.backend.extraction.condition;

import com.bokdeongi.backend.eligibility.Rule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 조건을 뽑을 공고 하나 (benefits 원문 + 지금 있는 코드 규칙).
 *
 * @param benefitId      benefits.id
 * @param serviceId      benefits.service_id (응답과 맞추는 키)
 * @param apiModifiedAt  공고 수정일시. 저장할 때 이 값이 그대로일 때만 쓴다 (그사이 동기화로 바뀌면 버림)
 * @param codeRules      지원조건 코드 규칙 (DATA-02). 코드와 모순 검사에 쓴다 (D-61)
 * @param manualCurrent  지금 원문으로 만든 수동(검수 라벨) 규칙이 있으면 true. 라벨이 우선이라 건너뛴다 (D-93)
 */
public record ConditionSource(long benefitId, String serviceId, String title, String orgName, String targetText,
        String criteriaText, String contentText, String applyMethodText, LocalDateTime apiModifiedAt,
        List<Rule> codeRules, boolean manualCurrent) {

    public ConditionSource {
        codeRules = codeRules == null ? List.of() : List.copyOf(codeRules);
    }

    /** 근거 문구를 대조할 원문: 서비스명·지원대상·선정기준·지원내용·신청방법 (C 채점기와 같음). 이 공고 것만 쓴다 */
    public List<String> sources() {
        List<String> out = new ArrayList<>();
        for (String s : new String[] {title, targetText, criteriaText, contentText, applyMethodText}) {
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 원문에 연도가 없는 숫자·날짜 판단용 기준 연도. */
    public Integer baseYear() {
        return apiModifiedAt == null ? null : apiModifiedAt.getYear();
    }
}
