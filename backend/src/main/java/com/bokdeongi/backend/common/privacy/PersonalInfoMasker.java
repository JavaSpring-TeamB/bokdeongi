package com.bokdeongi.backend.common.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자가 쓴 글을 LLM에 보내기 전에 개인정보를 가린다 (D-92 예외: 공고문 질문 BEN-08, 쉬운 말 번역기 EASY-02).
 * 주민등록번호·휴대전화·이메일·계좌번호·상세주소(동·층·호)를 가린다.
 * 기관 대표번호(02-, 1588- 등)와 도로명·건물번호까지의 주소는 안내에 필요해서 남긴다.
 */
public final class PersonalInfoMasker {

    public static final String MASK = "[가림]";

    private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{6}\\s*-?\\s*[1-8]\\d{6}(?!\\d)"); // 더 긴 숫자(계좌) 안의 13자리는 안 본다
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    /** 계좌: 숫자 묶음 3~4개, 숫자 합계 10~16자리 (OCR 서버 masking.py와 같은 기준). 날짜(2026-11-20)는 8자리라 안 걸린다 */
    private static final Pattern ACCOUNT = Pattern.compile("(?<![\\d*])\\d{2,6}(?:-\\d{2,7}){2,3}(?![\\d*])");
    /** 하이픈 없는 계좌(가상계좌 등): 10~16자리 연속 숫자. 쉼표 금액(1,833,000)·날짜는 안 걸린다 */
    private static final Pattern ACCOUNT_PLAIN = Pattern.compile("(?<![\\d*])\\d{10,16}(?![\\d*])");
    /** 기관 일반전화(02-123-4567, 031-1234-5678)는 계좌로 보지 않는다 */
    private static final Pattern LANDLINE = Pattern.compile("0(?:2|[3-6]\\d)-\\d{3,4}-\\d{4}");
    /**
     * 상세주소: "101동 1203호", "3층 301호", "101호". 앞이 한글·숫자면 안 본다("광진1동", "제3호").
     * "2호선"처럼 뒤에 '선'이 붙으면 안 본다
     */
    private static final Pattern ADDRESS_DETAIL = Pattern.compile(
            "(?<![가-힣\\d])(?:\\d{1,4}\\s*동\\s*)?(?:\\d{1,3}\\s*층\\s*)?\\d{1,5}\\s*호(?!선)");

    private PersonalInfoMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String s = RRN.matcher(text).replaceAll(MASK);
        s = PHONE.matcher(s).replaceAll(MASK);
        s = EMAIL.matcher(s).replaceAll(MASK);
        s = maskAccounts(s);
        s = ACCOUNT_PLAIN.matcher(s).replaceAll(MASK);
        return ADDRESS_DETAIL.matcher(s).replaceAll(MASK);
    }

    private static String maskAccounts(String s) {
        Matcher m = ACCOUNT.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String raw = m.group();
            long digits = raw.chars().filter(Character::isDigit).count();
            boolean account = digits >= 10 && digits <= 16 && !LANDLINE.matcher(raw).matches();
            m.appendReplacement(out, Matcher.quoteReplacement(account ? MASK : raw));
        }
        m.appendTail(out);
        return out.toString();
    }
}
