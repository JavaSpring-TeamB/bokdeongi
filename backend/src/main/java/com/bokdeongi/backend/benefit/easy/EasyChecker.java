package com.bokdeongi.backend.benefit.easy;

import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.extraction.Item;
import com.bokdeongi.backend.extraction.Purpose;
import com.bokdeongi.backend.extraction.Validation;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM이 낸 쉬운 말 JSON을 읽고 검사한다 (EASY-01, AGENTS.md 1·7번). 하나라도 걸리면 버린다.
 *
 * - 형식: JSON 객체, text가 있어야 함, 칸 길이 (요약 100자 = DB 칸 길이, 전체 1500자)
 * - 숫자: 글 속 숫자가 모두 원문 어딘가에 있어야 함 (ExtractionValidator EASY, 계산해서 만든 숫자도 버림)
 * - 판정 금지: "당신은 대상이에요"처럼 읽는 사람의 자격을 말하면 버림 (자격 판정은 규칙 엔진만)
 */
public final class EasyChecker {

    static final int MAX_SUMMARY = 100;
    static final int MAX_TEXT = 1500;

    /** 읽는 사람에게 자격을 말하는 문장 */
    private static final Pattern VERDICT = Pattern.compile(
            "(?:당신|회원님|고객님|귀하|여러분)[은는이가께서]*\\s*(?:\\S+\\s*){0,4}?(?:대상|받을\\s*수|해당)");
    private static final Pattern FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    /** 쉬운 말 한 벌 */
    public record EasyText(String who, String what, String how, String when, String text) {
    }

    /** @param easy 통과하면 값, 아니면 null / @param reason 버린 이유 */
    public record Result(EasyText easy, String reason) {
        public boolean ok() {
            return easy != null;
        }
    }

    private EasyChecker() {
    }

    /**
     * @param json    모델 출력
     * @param sources 공고 원문 칸들 (숫자 대조용)
     */
    public static Result check(String json, List<String> sources) {
        Object v;
        try {
            v = JsonText.parse(FENCE.matcher(json == null ? "" : json.strip()).replaceAll(""));
        } catch (IllegalArgumentException e) {
            return fail("JSON이 아님");
        }
        if (!(v instanceof Map<?, ?> m)) {
            return fail("JSON 객체가 아님");
        }
        EasyText e = new EasyText(str(m.get("who")), str(m.get("what")), str(m.get("how")), str(m.get("when")),
                str(m.get("text")));
        if (e.text() == null) {
            return fail("text 없음");
        }
        for (String s : List.of(nz(e.who()), nz(e.what()), nz(e.how()), nz(e.when()))) {
            if (s.length() > MAX_SUMMARY) {
                return fail("요약이 " + MAX_SUMMARY + "자를 넘음");
            }
        }
        if (e.text().length() > MAX_TEXT) {
            return fail("글이 " + MAX_TEXT + "자를 넘음");
        }
        String all = String.join("\n", nz(e.who()), nz(e.what()), nz(e.how()), nz(e.when()), e.text());
        if (VERDICT.matcher(all).find()) {
            return fail("읽는 사람의 자격을 말함");
        }
        Validation numbers = ExtractionValidator.validate(new Item("easy", null, null, null, null, null, all),
                Purpose.EASY, sources);
        if (numbers.dropped()) {
            return fail(numbers.reason());
        }
        return new Result(e, null);
    }

    private static Result fail(String reason) {
        return new Result(null, reason);
    }

    /** 빈 글·"null" 글은 null */
    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().strip();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
