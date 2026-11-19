package com.bokdeongi.backend.benefit.favorite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 중복 수혜 경고 (BEN-09). 관심·신청 중인 혜택끼리 중복 지원이 안 될 수 있으면 미리 알린다. DB·Spring에 의존하지 않는다.
 *
 * <p>중복 불가 문구(benefits.exclusions, DATA-06)는 "다른 월세 지원사업과 중복 불가"처럼 상대 혜택을 콕 집지 않는 경우가 많다.
 * 그래서 짝은 이렇게 찾는다.
 * <ul>
 *   <li>한쪽 중복 불가 문구 안에 상대 혜택 이름이 그대로 있으면 STRONG ("함께 받을 수 없어요")</li>
 *   <li>그렇지 않으면 두 혜택의 서비스 분야가 같거나 분야 해시태그(월세·장학금 등)가 겹칠 때만 CHECK
 *       ("중복 여부 확인 필요", 약하게. BEN-09 예외: 관계가 불확실하면 약하게 표시)</li>
 *   <li>한쪽이 신청 완료 후 결과가 나왔거나(선정·미선정·중단) 공고가 종료되면 경고 대상에서 빠진다 (부르는 쪽이 거른다)</li>
 * </ul>
 */
public final class DuplicateWarnings {

    public enum Level { STRONG, CHECK }

    /**
     * 관심·신청 중인 혜택 하나.
     *
     * @param fieldTags      분야 해시태그 (지원형태·대상 태그는 빼고)
     * @param exclusionQuote 중복 불가 문구. 없으면 null
     */
    public record Tracked(long benefitId, String title, String serviceField, Set<String> fieldTags,
                          String exclusionQuote) {
        public Tracked {
            fieldTags = fieldTags == null ? Set.of() : Set.copyOf(fieldTags);
        }
    }

    /**
     * 경고 하나.
     *
     * @param sourceQuote 근거가 된 중복 불가 문구 (어느 쪽 공고의 문구인지는 quoteBenefitId)
     */
    public record Warning(long otherBenefitId, String otherTitle, Level level, String sourceQuote,
                          long quoteBenefitId) {
    }

    private DuplicateWarnings() {
    }

    /** @return 혜택 id → 그 혜택 카드에 띄울 경고 (경고가 없는 혜택은 빠짐) */
    public static Map<Long, List<Warning>> find(List<Tracked> tracked) {
        Map<Long, List<Warning>> out = new LinkedHashMap<>();
        for (int i = 0; i < tracked.size(); i++) {
            for (int j = i + 1; j < tracked.size(); j++) {
                Tracked a = tracked.get(i);
                Tracked b = tracked.get(j);
                if (a.benefitId() == b.benefitId()) {
                    continue;
                }
                Warning chosen = pick(warning(a, b), warning(b, a));
                // 문구가 상대 이름을 콕 집었으면 분야와 상관없이, 아니면 같은 분야일 때만
                if (chosen == null || (chosen.level() == Level.CHECK && !related(a, b))) {
                    continue;
                }
                out.computeIfAbsent(a.benefitId(), k -> new ArrayList<>()).add(
                        new Warning(b.benefitId(), b.title(), chosen.level(), chosen.sourceQuote(),
                                chosen.quoteBenefitId()));
                out.computeIfAbsent(b.benefitId(), k -> new ArrayList<>()).add(
                        new Warning(a.benefitId(), a.title(), chosen.level(), chosen.sourceQuote(),
                                chosen.quoteBenefitId()));
            }
        }
        return out;
    }

    static boolean related(Tracked a, Tracked b) {
        if (a.serviceField() != null && Objects.equals(a.serviceField(), b.serviceField())) {
            return true;
        }
        Set<String> common = new HashSet<>(a.fieldTags());
        common.retainAll(b.fieldTags());
        return !common.isEmpty();
    }

    /** owner의 중복 불가 문구로 본 other에 대한 경고. 문구가 없으면 null */
    private static Warning warning(Tracked owner, Tracked other) {
        String quote = owner.exclusionQuote();
        if (quote == null || quote.isBlank()) {
            return null;
        }
        Level level = mentions(quote, other.title()) ? Level.STRONG : Level.CHECK;
        return new Warning(other.benefitId(), other.title(), level, quote, owner.benefitId());
    }

    private static Warning pick(Warning ab, Warning ba) {
        if (ab == null) {
            return ba;
        }
        if (ba == null) {
            return ab;
        }
        return ba.level() == Level.STRONG && ab.level() != Level.STRONG ? ba : ab;
    }

    /** 문구 안에 상대 이름이 그대로 있는지 (띄어쓰기 무시, 4글자 이상 이름만) */
    static boolean mentions(String quote, String title) {
        if (title == null) {
            return false;
        }
        String t = title.replaceAll("[\\s()\\[\\]]", "");
        return t.length() >= 4 && quote.replaceAll("\\s", "").contains(t);
    }
}
