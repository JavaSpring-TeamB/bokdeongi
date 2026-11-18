package com.bokdeongi.backend.benefit.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ② 서류 준비 (ASST-04). LLM을 부르지 않는다.
 *
 * 1. 공고 구비서류 칸에서 내가 낼 서류 이름을 뽑는다 (DocumentRuleExtractor, 공무원 확인 서류는 다른 칸이라 들어오지 않는다)
 * 2. 서류 목록(document_types, DATA-07)의 이름·별칭과 맞춘다. 같으면 그 서류, 아니면 이름 안에 들어 있는 가장 긴 이름·별칭
 * 3. 맞춘 서류는 발급하는 곳(정부24, 대법원 가족관계, 건강보험공단, 홈택스, 위택스, 그 밖의 발급처)별로 묶는다
 * 4. 신청서·동의서·사업계획서·서약서처럼 공고마다 양식이 다른 것은 "신청 기관 서식"으로 묶는다
 * 5. "서류", "증빙서류"처럼 이름이 뭉뚱그려진 것은 뺀다
 * 6. 그 밖에 목록에 없는 서류는 공고 원문 이름 그대로 따로 둔다 (화면에서 문의처와 함께 보여 준다)
 */
public final class DocumentPrepBuilder {

    /** document_types 한 줄 */
    public record MasterDoc(Long id, String name, List<String> aliases, String issuer, boolean onlineAvailable,
                            String issueUrl, String issueMethod, Integer defaultValidityDays, String alternatives,
                            boolean ocrSupported) {
    }

    /**
     * 준비할 서류 하나.
     *
     * @param sourceName 공고 원문의 서류 이름
     * @param doc        맞춘 서류 목록 줄 (발급 방법·유효기간·대체 서류)
     */
    public record PrepItem(String sourceName, MasterDoc doc) {
    }

    /** 발급하는 곳 하나와 거기서 받을 서류들 */
    public record IssuerGroup(String place, String url, List<PrepItem> items) {
    }

    /**
     * @param groups  발급하는 곳별 묶음 (서류가 많은 곳부터)
     * @param forms   신청 기관 서식 (신청서·동의서 등, 접수처에 비치되거나 공고 누리집에서 내려받는 것)
     * @param unknown 서류 목록에 없는 서류 (원문 이름 그대로)
     */
    public record Prep(List<IssuerGroup> groups, List<String> forms, List<String> unknown) {
        public int total() {
            return groups.stream().mapToInt(g -> g.items().size()).sum() + forms.size() + unknown.size();
        }
    }

    /** 발급 누리집 주소 → 화면에 보여 줄 이름 */
    private static final Map<String, String> SITE = Map.of(
            "https://www.gov.kr", "정부24",
            "https://efamily.scourt.go.kr", "대법원 전자가족관계등록시스템",
            "https://www.nhis.or.kr", "국민건강보험공단",
            "https://www.hometax.go.kr", "홈택스",
            "https://www.wetax.go.kr", "위택스");

    /** 공고마다 양식이 다른 서식 */
    private static final java.util.regex.Pattern FORM = java.util.regex.Pattern.compile(
            "(?:신청서|동의서|계획서|서약서|청구서|신고서|추천서|자기소개서|확약서|위임장)$");
    /** 이름만으로는 무슨 서류인지 알 수 없는 것 */
    private static final java.util.regex.Pattern GENERIC = java.util.regex.Pattern.compile(
            "^(?:관련|기타|증빙|추가|공무원\\s*확인\\s*가능|필요|해당)?\\s*(?:서류|증빙서류|증빙자료|자료)$");

    /** 이보다 짧은 별칭("등본")은 이름 안에 들어 있어도 맞추지 않고, 같을 때만 맞춘다 */
    private static final int MIN_CONTAINS = 3;

    private DocumentPrepBuilder() {
    }

    public static Prep build(String requiredDocsText, List<MasterDoc> master) {
        Map<String, List<PrepItem>> byPlace = new LinkedHashMap<>();
        Map<String, String> urlOf = new LinkedHashMap<>();
        List<String> forms = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<Long> seen = new ArrayList<>();

        for (String name : DocumentRuleExtractor.extract(requiredDocsText)) {
            if (GENERIC.matcher(name.strip()).matches()) {
                continue;
            }
            MasterDoc doc = match(name, master);
            if (doc == null) {
                List<String> to = FORM.matcher(key(name)).find() ? forms : unknown;
                if (!to.contains(name)) {
                    to.add(name);
                }
                continue;
            }
            if (doc.id() != null && seen.contains(doc.id())) {
                continue; // "주민등록등본", "주민등록표 등본"이 둘 다 적힌 경우 한 번만
            }
            seen.add(doc.id());
            String url = nz(doc.issueUrl());
            String place = SITE.getOrDefault(url, nz(doc.issuer()).isBlank() ? "발급처 확인 필요" : doc.issuer());
            byPlace.computeIfAbsent(place, k -> new ArrayList<>()).add(new PrepItem(name, doc));
            urlOf.putIfAbsent(place, url.isBlank() ? null : url);
        }

        List<IssuerGroup> groups = new ArrayList<>();
        byPlace.forEach((place, items) -> groups.add(new IssuerGroup(place, urlOf.get(place), List.copyOf(items))));
        groups.sort((a, b) -> b.items().size() - a.items().size()); // 한 곳에서 많이 받을수록 위로
        return new Prep(List.copyOf(groups), List.copyOf(forms), List.copyOf(unknown));
    }

    /** 공고 서류 이름을 서류 목록과 맞춘다. 못 맞추면 null */
    static MasterDoc match(String sourceName, List<MasterDoc> master) {
        String src = key(sourceName);
        MasterDoc best = null;
        int bestLen = 0;
        for (MasterDoc doc : master) {
            List<String> names = new ArrayList<>();
            names.add(doc.name());
            if (doc.aliases() != null) {
                names.addAll(doc.aliases());
            }
            for (String n : names) {
                String k = key(n);
                if (k.isEmpty()) {
                    continue;
                }
                if (k.equals(src)) {
                    return doc;
                }
                if (k.length() >= MIN_CONTAINS && src.contains(k) && k.length() > bestLen) {
                    best = doc;
                    bestLen = k.length();
                }
            }
        }
        return best;
    }

    /** 띄어쓰기·가운뎃점·마침표를 빼고 비교한다 ("통장 사본" = "통장사본") */
    static String key(String s) {
        return nz(s).replaceAll("[\\s·ㆍ.]", "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
