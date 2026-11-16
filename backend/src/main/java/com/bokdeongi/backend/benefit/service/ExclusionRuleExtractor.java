package com.bokdeongi.backend.benefit.service;

import java.util.regex.Pattern;

/**
 * 중복 지원 불가 문구를 규칙으로 찾는다. LLM을 부르지 않는다. (DATA-06)
 *
 * 지원대상 → 선정기준 → 지원내용 순서로 줄을 보고, "중복"과 불가·제외·금지 같은 말이 가까이 붙은 첫 줄을 그대로 돌려준다.
 * "중복 보장 가능", "중복장애인"처럼 불가가 아닌 말은 뺀다. 없으면 빈 문자열.
 * 괄호 안의 예외는 빼고 판단한다: "(단, 긴급지원은 중복 가능)"은 통째로, "(중복 수혜 불가, 단 긴급지원은 예외)"는 '단' 뒤만.
 */
public final class ExclusionRuleExtractor {

    private static final Pattern EXCLUSION = Pattern.compile(
            "중복(?!\\s*장애)[^\\n]{0,15}?(?:불가|제외|금지|안\\s*됨|않|없|받은\\s*자|택\\s*1|X|제한|해당되는)");
    private static final Pattern POSITIVE = Pattern.compile("중복\\s*(?:보장|보상|지원|수혜|적용)?\\s*(?:이\\s*)?가능");
    /** 괄호 안 '단' 뒤의 예외: "(중복 수혜 불가, 단 긴급지원은 예외)" → "(중복 수혜 불가" + ")" */
    private static final Pattern PAREN_BUT = Pattern.compile("(\\([^)]*?)[\\s,]*단[\\s,.]+[^)]*\\)");
    /** '단'을 지우고도 '가능'이 남은 괄호는 예외 설명이라 통째로 뺀다: "(중복 가능)" */
    private static final Pattern PAREN_POSITIVE = Pattern.compile("\\([^)]*가능[^)]*\\)");
    private static final Pattern BULLET = Pattern.compile("^[\\-○ㅇ·•▶◦※*\\d.)\\s]+");

    private ExclusionRuleExtractor() {
    }

    /** texts: 지원대상, 선정기준, 지원내용 순서 */
    public static String extract(String... texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (String line : text.split("\n")) {
                String core = PAREN_POSITIVE.matcher(PAREN_BUT.matcher(line).replaceAll("$1)")).replaceAll("");
                if (EXCLUSION.matcher(core).find() && !POSITIVE.matcher(core).find()) {
                    return BULLET.matcher(line).replaceFirst("").strip();
                }
            }
        }
        return "";
    }
}
