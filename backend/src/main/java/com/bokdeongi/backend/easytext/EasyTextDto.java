package com.bokdeongi.backend.easytext;

import java.util.List;

/** 쉬운 말 번역기 요청·응답 (EASY-02, POST /api/easy-text) */
public final class EasyTextDto {

    public static final String NOTICE = "AI가 바꾼 문장이에요. 정확한 내용은 원문과 발급 기관에 확인하세요";

    private EasyTextDto() {
    }

    /** @param text 고지서·안내문 글 (5,000자 이내). 개인정보는 지우고 붙여 넣는다 */
    public record Request(String text) {
    }

    /**
     * 묶음 하나. 화면의 '쉬운 말'·'원문 비교'·'근거' 탭이 같은 목록을 쓴다.
     *
     * @param category   묶음 이름 ("소득 조건" 등)
     * @param original   원문 문장 (가린 뒤 원문에 글자 그대로 있는 것만)
     * @param simplified 쉬운 말
     * @param action     할 일 (없으면 null)
     * @param caution    주의할 점 (없으면 null)
     */
    public record Section(String category, String original, String simplified, String action, String caution) {
    }

    /**
     * @param status    DONE / FAILED. FAILED면 summary에 안내 문구, sections는 비어 있다
     * @param summary   한 줄 요약
     * @param sections  묶음 목록 (공공 안내문이 아니면 비어 있을 수 있다)
     * @param masked    개인정보(주민번호·휴대전화·이메일)를 가리고 보냈는지. true면 화면에 "개인정보를 가리고 보냈어요" 표시
     * @param remaining 오늘 남은 횟수
     * @param notice    화면에 늘 붙이는 안내
     */
    public record Response(String status, String summary, List<Section> sections, boolean masked, int remaining,
                           String notice) {
    }
}
