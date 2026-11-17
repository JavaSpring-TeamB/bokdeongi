package com.bokdeongi.backend.benefit.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 해시태그를 규칙으로 붙인다. LLM을 부르지 않는다. (DATA-09)
 *
 * 분야 1~2개 + 지원형태 1개 + 대상 0~2개, 최대 5개. 이름은 hashtags_seed.csv와 같다.
 * - 분야: API 서비스분야 → 기본 태그, 서비스명 낱말로 더 좁은 태그(월세·전세·창업 등)
 * - 지원형태: API 지원유형
 * - 대상: 지원조건 코드(JA)와 나이, 사용자구분, 서비스명·지원대상 낱말
 */
public final class HashtagRuleTagger {

    /** 공고 한 건에서 태그를 고를 때 쓰는 값 (API 원본 그대로) */
    public record Input(String title, String serviceField, String supportType, String userType,
                        String targetText, String contentText, Set<String> jaCodes,
                        Integer ageMin, Integer ageMax) {
    }

    private static final int MAX_FIELD = 2;
    private static final int MAX_TARGET = 2;

    /** 서비스분야 → 분야 태그 */
    private static final Map<String, String> FIELD = Map.of(
            "생활안정", "생활비",
            "주거·자립", "주거",
            "고용·창업", "일자리",
            "보육·교육", "교육",
            "임신·출산", "임신출산",
            "보건·의료", "의료",
            "보호·돌봄", "돌봄",
            "문화·환경", "문화여가",
            "농림축산어업", "농어업",
            "행정·안전", "안전");

    /** 낱말로 붙이는 더 좁은 분야 태그 (먼저 맞는 것부터) */
    private static final List<Map.Entry<String, Pattern>> FIELD_WORDS = List.of(
            Map.entry("월세", Pattern.compile("월세")),
            Map.entry("전세", Pattern.compile("전세")),
            Map.entry("창업", Pattern.compile("창업")),
            Map.entry("장학금", Pattern.compile("장학금|장학생")),
            Map.entry("보육", Pattern.compile("어린이집|보육|양육수당|아이돌봄")),
            Map.entry("건강검진", Pattern.compile("검진|예방접종|접종")),
            Map.entry("교통", Pattern.compile("교통비|대중교통|버스|택시|교통카드")),
            Map.entry("법률상담", Pattern.compile("법률|소송|변호")));

    /** 지원유형 → 지원형태 태그 */
    private static final Map<String, String> FORM = Map.ofEntries(
            Map.entry("현금", "현금지원"),
            Map.entry("현금(장학금)", "현금지원"),
            Map.entry("현금(보험)", "현금지원"),
            Map.entry("현금(융자)", "대출"),
            Map.entry("현금(감면)", "감면"),
            Map.entry("현물", "현물"),
            Map.entry("이용권", "바우처"),
            Map.entry("서비스(의료)", "서비스"),
            Map.entry("서비스(돌봄)", "서비스"),
            Map.entry("서비스(일자리)", "서비스"),
            Map.entry("시설이용", "서비스"),
            Map.entry("의료지원", "서비스"),
            Map.entry("문화/여가지원", "서비스"),
            Map.entry("기타(상담)", "서비스"),
            Map.entry("상담/법률지원", "서비스"),
            Map.entry("기타(교육)", "서비스"));

    // 지원조건 코드 묶음. "해당사항 없음"(JA0322·JA0410)이 Y면 그 묶음은 대상을 좁히지 않는다
    private static final List<String> JA03 = List.of("JA0301", "JA0302", "JA0303", "JA0313", "JA0314", "JA0315",
            "JA0316", "JA0317", "JA0318", "JA0319", "JA0320", "JA0322", "JA0326", "JA0327", "JA0328", "JA0329", "JA0330");
    private static final List<String> JA04 = List.of("JA0401", "JA0402", "JA0403", "JA0404", "JA0410", "JA0411",
            "JA0412", "JA0413", "JA0414");

    /** 대상 태그와 그 근거 (코드 / 낱말). 위에 있을수록 먼저 고른다 */
    private record TargetRule(String tag, List<String> codes, Pattern words) {
    }

    private static final List<TargetRule> TARGETS = List.of(
            new TargetRule("한부모", List.of("JA0403"), Pattern.compile("한부모|조손")),
            new TargetRule("장애인", List.of("JA0328"), Pattern.compile("장애인|장애아")),
            new TargetRule("보훈", List.of("JA0329"), Pattern.compile("보훈|유공자")),
            new TargetRule("다문화", List.of("JA0401", "JA0402"), Pattern.compile("다문화|북한이탈|새터민")),
            new TargetRule("임산부", List.of("JA0302"), Pattern.compile("임산부|임신부")),
            new TargetRule("신혼부부", List.of(), Pattern.compile("신혼")),
            new TargetRule("다자녀", List.of("JA0411"), Pattern.compile("다자녀|다둥이")),
            new TargetRule("1인가구", List.of("JA0404"), Pattern.compile("1인\\s*가구")),
            new TargetRule("대학생", List.of("JA0320"), Pattern.compile("대학생|대학원생")),
            new TargetRule("구직자", List.of("JA0327"), Pattern.compile("구직|미취업|실업")),
            new TargetRule("농어민", List.of("JA0313", "JA0314", "JA0315", "JA0316"), Pattern.compile("농업인|어업인|농가|어가|축산농가")),
            new TargetRule("청년", List.of(), Pattern.compile("청년")),
            new TargetRule("노인", List.of(), Pattern.compile("노인|어르신|경로|만\\s*6[05]세\\s*이상")),
            new TargetRule("아동", List.of("JA0317", "JA0318", "JA0319"), Pattern.compile("아동|영유아|어린이|청소년|초등|중학생|고등학생|학생")),
            new TargetRule("저소득", List.of(), Pattern.compile("수급자|차상위|저소득|중위소득")));

    private HashtagRuleTagger() {
    }

    public static List<String> tag(Input in) {
        String title = nz(in.title());
        String words = title; // 지원내용까지 보면 "버스", "검진" 같은 말에 잘못 걸려서 서비스명만 본다
        String people = title + " " + nz(in.targetText());
        Set<String> codes = in.jaCodes() == null ? Set.of() : in.jaCodes();

        // 분야: 좁은 낱말 태그가 있으면 먼저, 그다음 서비스분야 태그
        Set<String> fields = new LinkedHashSet<>();
        for (Map.Entry<String, Pattern> e : FIELD_WORDS) {
            if (e.getValue().matcher(words).find()) {
                fields.add(e.getKey());
            }
        }
        if (nz(in.supportType()).contains("현금(장학금)")) {
            fields.add("장학금");
        }
        if (nz(in.supportType()).contains("상담")) {
            fields.add("법률상담");
        }
        for (String f : split(in.serviceField())) {
            if (FIELD.containsKey(f)) {
                fields.add(FIELD.get(f));
            }
        }

        // 지원형태: 첫 번째 지원유형
        String form = null;
        for (String t : split(in.supportType())) {
            form = FORM.get(t);
            if (form != null) {
                break;
            }
        }

        // 대상: 지원조건 코드가 대상을 좁히면 그걸, 아니면 서비스명·지원대상 낱말
        Set<String> targets = new LinkedHashSet<>();
        if ("소상공인".equals(nz(in.userType()).strip()) || nz(in.userType()).contains("소상공인")) {
            targets.add("소상공인");
        }
        boolean ja03 = narrows(codes, JA03, "JA0322");
        boolean ja04 = narrows(codes, JA04, "JA0410");
        for (TargetRule rule : TARGETS) {
            boolean byCode = rule.codes().stream().anyMatch(c ->
                    codes.contains(c) && (JA03.contains(c) ? ja03 : ja04));
            if (byCode || rule.words().matcher(people).find()) {
                targets.add(rule.tag());
            }
        }
        if (in.ageMax() != null && in.ageMin() != null) {
            if (in.ageMin() >= 15 && in.ageMax() <= 39) {
                targets.add("청년");
            } else if (in.ageMin() >= 60) {
                targets.add("노인");
            } else if (in.ageMax() <= 18) {
                targets.add("아동");
            }
        }
        if (codes.contains("JA0201") && !codes.contains("JA0203") && !codes.contains("JA0204")
                && !codes.contains("JA0205")) {
            targets.add("저소득"); // 중위소득 75% 이하만
        }

        List<String> result = new ArrayList<>(fields.stream().limit(MAX_FIELD).toList());
        if (form != null) {
            result.add(form);
        }
        result.addAll(targets.stream().filter(t -> !result.contains(t)).limit(MAX_TARGET).toList());
        return result;
    }

    /** 그 묶음에 Y가 있지만 전부 Y는 아니고, "해당사항 없음"도 Y가 아닐 때만 대상을 좁힌다 */
    private static boolean narrows(Set<String> codes, List<String> group, String none) {
        long yes = group.stream().filter(codes::contains).count();
        return yes > 0 && yes < group.size() && !codes.contains(none);
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String part : nz(s).split("\\|\\||,")) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
