package com.bokdeongi.backend.benefit.ask;

import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.extraction.Item;
import com.bokdeongi.backend.extraction.Purpose;
import com.bokdeongi.backend.extraction.Validation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM이 낸 답 JSON을 읽고 검사한다 (BEN-08, AGENTS.md 1·7번). 걸리면 답을 버리고 "공고에서 못 찾았어요"로 바꾼다.
 *
 * - 근거 문장(quotes)은 원문에 글자 그대로 있어야 한다 (ExtractionValidator.quoteFound). 없는 문장은 뺀다
 * - found = true인데 남은 근거가 없으면 답을 버린다 (지어낸 근거)
 * - 답 속 숫자는 모두 원문에 있어야 한다 (EASY 검사와 같다)
 * - 묻는 사람의 자격을 말하면("받을 수 있어요", "대상이에요") 버린다. 자격 결론은 규칙 엔진만 낸다
 */
public final class AskChecker {

    static final int MAX_ANSWER = 500;
    static final int MAX_QUOTES = 3;

    /** 자격 결론처럼 들리는 서술어 */
    private static final Pattern VERDICT_PRED = Pattern.compile(
            "받(?:을|으실)\\s*수\\s*있|신청(?:할|하실)\\s*수\\s*있|대상이(?:에요|세요|십니다|입니다)|해당(?:돼요|되세요|됩니다|하세요)");
    /** 묻는 사람을 가리키는 말 */
    private static final Pattern YOU = Pattern.compile("당신|회원님|고객님|귀하|질문하신\\s*분|사용자님");
    /** 서술어 앞에 주어(대상 설명)가 있는지: "만 19~34세 청년이", "휴학생은" */
    private static final Pattern SUBJECT = Pattern.compile("[가-힣0-9A-Za-z)]\\s*(?:은|는|이|가|도)(?:\\s|$)");
    private static final Pattern FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    /** @param answer 통과한 답 (null이면 버림) / @param reason 버린 이유 */
    public record Result(String answer, boolean found, List<String> quotes, boolean eligibility, String reason) {
        public boolean ok() {
            return answer != null;
        }
    }

    private AskChecker() {
    }

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
        String answer = m.get("answer") == null ? "" : m.get("answer").toString().strip();
        if (answer.isEmpty()) {
            return fail("answer 없음");
        }
        if (answer.length() > MAX_ANSWER) {
            return fail("답이 " + MAX_ANSWER + "자를 넘음");
        }
        boolean found = Boolean.TRUE.equals(m.get("found"));
        boolean eligibility = Boolean.TRUE.equals(m.get("eligibility"));

        List<String> quotes = new ArrayList<>();
        if (m.get("quotes") instanceof List<?> list) {
            for (Object q : list) {
                String s = q == null ? "" : q.toString().strip();
                if (!s.isEmpty() && ExtractionValidator.quoteFound(s, sources) && !quotes.contains(s)
                        && quotes.size() < MAX_QUOTES) {
                    quotes.add(s);
                }
            }
        }
        if (found && quotes.isEmpty()) {
            return fail("근거 문장이 원문에 없음");
        }
        if (!found) {
            quotes = List.of();
        }
        if (verdict(answer)) {
            return fail("묻는 사람의 자격을 말함");
        }
        Validation numbers = ExtractionValidator.validate(new Item("ask", null, null, null, null, null, answer),
                Purpose.EASY, sources);
        if (numbers.dropped()) {
            return fail(numbers.reason());
        }
        return new Result(answer, found, List.copyOf(quotes), eligibility, null);
    }

    /**
     * 자격 판정 문장인지. 문장마다 본다.
     * - "네, 받을 수 있어요" / "회원님은 대상이에요" → 판정
     * - "받을 수 있어요."처럼 주어 없이 결론만 → 판정
     * - "만 19~34세 청년이 받을 수 있어요" → 대상 설명이라 괜찮다
     */
    static boolean verdict(String answer) {
        for (String sentence : answer.split("(?<=[.!?。])\\s*")) {
            Matcher pred = VERDICT_PRED.matcher(sentence);
            if (!pred.find()) {
                continue;
            }
            String s = sentence.strip();
            if (s.matches("^(?:네|예)(?:[,.!\\s].*|$)") || YOU.matcher(s).find()) {
                return true;
            }
            if (!SUBJECT.matcher(sentence.substring(0, pred.start())).find()) {
                return true;
            }
        }
        return false;
    }

    private static Result fail(String reason) {
        return new Result(null, false, List.of(), false, reason);
    }
}
