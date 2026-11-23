package com.bokdeongi.backend.easytext;

import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.extraction.Item;
import com.bokdeongi.backend.extraction.Purpose;
import com.bokdeongi.backend.extraction.Validation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM이 낸 쉬운 말 번역 JSON을 읽고 검사한다 (EASY-02, AGENTS.md 1·7번).
 *
 * - 묶음의 original은 (가린 뒤) 원문에 글자 그대로 있어야 한다. 없는 묶음은 뺀다
 * - 묶음이 있었는데 다 빠지면 버린다 (지어낸 근거)
 * - 글 속 숫자는 모두 원문에 있어야 한다 (공고문 쉬운 말 EasyChecker와 같은 EASY 검사, 하나라도 걸리면 버림)
 * - 읽는 사람의 자격을 말하면("당신은 대상이에요") 버린다
 */
public final class EasyTextChecker {

    static final int MAX_SECTIONS = 8;
    static final int MAX_SUMMARY = 150;
    static final int MAX_CATEGORY = 20;
    static final int MAX_FIELD = 300;

    private static final Pattern VERDICT = Pattern.compile(
            "(?:당신|회원님|고객님|귀하|여러분)[은는이가께서]*\\s*(?:\\S+\\s*){0,4}?(?:대상|받을\\s*수|해당)");
    private static final Pattern FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    /** @param summary 통과하면 값, 아니면 null / @param reason 버린 이유 */
    public record Result(String summary, List<EasyTextDto.Section> sections, String reason) {
        public boolean ok() {
            return summary != null;
        }
    }

    private EasyTextChecker() {
    }

    /**
     * @param json   모델 출력
     * @param source 가린 뒤의 원문 (모델에 보낸 글)
     */
    public static Result check(String json, String source) {
        Object v;
        try {
            v = JsonText.parse(FENCE.matcher(json == null ? "" : json.strip()).replaceAll(""));
        } catch (IllegalArgumentException e) {
            return fail("JSON이 아님");
        }
        if (!(v instanceof Map<?, ?> m)) {
            return fail("JSON 객체가 아님");
        }
        String summary = str(m.get("summary"));
        if (summary == null) {
            return fail("summary 없음");
        }
        if (summary.length() > MAX_SUMMARY) {
            return fail("요약이 " + MAX_SUMMARY + "자를 넘음");
        }
        List<String> sources = List.of(source);
        List<Object> raw = m.get("sections") instanceof List<?> l ? new ArrayList<>(l) : List.of();
        List<EasyTextDto.Section> sections = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> s) || sections.size() >= MAX_SECTIONS) {
                continue;
            }
            String original = str(s.get("original"));
            String simplified = str(s.get("simplified"));
            if (original == null || simplified == null || !ExtractionValidator.quoteFound(original, sources)) {
                continue; // 원문에 없는 근거는 뺀다
            }
            String category = str(s.get("category"));
            String action = str(s.get("action"));
            String caution = str(s.get("caution"));
            if ((category != null && category.length() > MAX_CATEGORY) || simplified.length() > MAX_FIELD
                    || (action != null && action.length() > MAX_FIELD)
                    || (caution != null && caution.length() > MAX_FIELD)) {
                return fail("묶음 글이 너무 김");
            }
            sections.add(new EasyTextDto.Section(category == null ? "내용" : category, original, simplified, action,
                    caution));
        }
        if (!raw.isEmpty() && sections.isEmpty()) {
            return fail("근거 원문이 원문에 없음");
        }

        StringBuilder all = new StringBuilder(summary);
        for (EasyTextDto.Section s : sections) {
            all.append('\n').append(s.simplified());
            if (s.action() != null) {
                all.append('\n').append(s.action());
            }
            if (s.caution() != null) {
                all.append('\n').append(s.caution());
            }
        }
        if (VERDICT.matcher(all).find()) {
            return fail("읽는 사람의 자격을 말함");
        }
        Validation numbers = ExtractionValidator.validate(
                new Item("easy-text", null, null, null, null, null, all.toString()), Purpose.EASY, sources);
        if (numbers.dropped()) {
            return fail(numbers.reason());
        }
        return new Result(summary, List.copyOf(sections), null);
    }

    private static Result fail(String reason) {
        return new Result(null, List.of(), reason);
    }

    /** 빈 글·"null" 글은 null */
    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().strip();
        return s.isEmpty() || s.equalsIgnoreCase("null") ? null : s;
    }
}
