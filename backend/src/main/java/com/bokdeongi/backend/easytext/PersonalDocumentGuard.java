package com.bokdeongi.backend.easytext;

import java.util.regex.Pattern;

/**
 * 쉬운 말 번역기에 개인 서류(등본·증명서 등)가 들어오면 AI로 보내기 전에 막는다 (D-78: 서류 내용은 LLM에 안 보냄).
 * 글 붙여 넣기와 사진·PDF 둘 다 지나간다. OCR 서버는 아는 4종만 막으므로 그 밖의 서류(가족관계증명서 등)는 여기서 막는다.
 *
 * <p>제목만 본다: 앞쪽 {@value #TITLE_LINES}줄 중 공백을 뺀 길이가 {@value #TITLE_MAX}자 이하이고
 * 서류 이름으로 끝나는 줄("가족관계증명서", "주민등록표(등본)"). 본문 문장("소득금액증명원을 내세요")이나
 * "소득금액증명원 제출 안내" 같은 안내문 제목은 서류 이름으로 끝나지 않아 통과한다.
 */
final class PersonalDocumentGuard {

    static final int TITLE_LINES = 3;
    static final int TITLE_MAX = 30;

    /** OCR이 "주 민 등 록 표", "(등 본)"처럼 띄어 읽으므로 공백을 뺀 글에서 찾는다. 끝에 괄호 하나("(등본)")는 붙어도 된다 */
    private static final Pattern PERSONAL = Pattern.compile("(?:주민등록표|등본|초본|증명서|증명원|확인서)(?:\\([^()]*\\))?[:.]?$");

    private PersonalDocumentGuard() {
    }

    static boolean looksPersonal(String text) {
        if (text == null) {
            return false;
        }
        int seen = 0;
        for (String line : text.split("\\R")) {
            String flat = line.replaceAll("\\s+", "");
            if (flat.isEmpty()) {
                continue;
            }
            if (flat.length() <= TITLE_MAX && PERSONAL.matcher(flat).find()) {
                return true;
            }
            if (++seen >= TITLE_LINES) {
                break;
            }
        }
        return false;
    }
}
