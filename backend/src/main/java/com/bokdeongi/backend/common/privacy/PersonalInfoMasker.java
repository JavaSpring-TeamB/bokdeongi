package com.bokdeongi.backend.common.privacy;

import java.util.regex.Pattern;

/**
 * 사용자가 쓴 글을 LLM에 보내기 전에 개인정보를 가린다 (D-92 예외: 공고문 질문 BEN-08, 쉬운 말 번역기 EASY-02).
 * 주민등록번호·휴대전화·이메일만 가린다. 기관 대표번호(02-, 1588- 등)는 안내에 필요해서 남긴다.
 */
public final class PersonalInfoMasker {

    public static final String MASK = "[가림]";

    private static final Pattern RRN = Pattern.compile("\\d{6}\\s*-?\\s*[1-8]\\d{6}");
    private static final Pattern PHONE = Pattern.compile("01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    private PersonalInfoMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String s = RRN.matcher(text).replaceAll(MASK);
        s = PHONE.matcher(s).replaceAll(MASK);
        return EMAIL.matcher(s).replaceAll(MASK);
    }
}
