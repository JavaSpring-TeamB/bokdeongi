package com.bokdeongi.backend.benefit.ask;

import java.util.List;

/** 공고문 질문 요청·응답 (BN-08) */
public final class AskDto {

    private AskDto() {
    }

    /** @param question 질문 (200자 이내) */
    public record Request(String question) {
    }

    /**
     * @param answer        답 (원문 근거, 해요체)
     * @param foundInSource 원문에서 답을 찾았는지. false면 answer에 문의처가 붙는다
     * @param sourceQuotes  근거 원문 문장 (원문에 글자 그대로 있는 것만)
     * @param eligibility   자격을 묻는 질문인지. true면 프론트가 '내 판정 결과' 링크를 붙인다 (자격 결론은 규칙 엔진, D-08)
     * @param remaining     오늘 남은 질문 횟수
     * @param notice        화면에 늘 붙이는 안내
     */
    public record Response(String answer, boolean foundInSource, List<String> sourceQuotes, boolean eligibility,
                           int remaining, String notice) {
    }

    public static final String NOTICE = "AI가 공고 원문을 보고 답한 내용이에요. 정확한 내용은 원문과 문의처로 확인하세요";
}
