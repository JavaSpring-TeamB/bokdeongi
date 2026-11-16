package com.bokdeongi.backend.eligibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 지원조건 코드 → 1차 판정 규칙 (DATA-02).
 *
 * <p>공공서비스 API의 supportConditions 응답(JA 코드 → "Y" 또는 빈 값, 나이는 숫자)과 사용자구분을 받아
 * 규칙 엔진이 읽는 규칙 목록과 개인 추천 대상 구분(benefits.target_scope)을 만든다. DB·Spring에 의존하지 않는다.
 * 대응표는 노션 기능 명세 DATA-02 페이지 (D-48, D-49, D-50).
 *
 * <ul>
 *   <li>그룹 안 코드가 전부 Y이거나, Y가 없거나, '해당사항없음'(JA0322·JA0410)이 Y면 그 그룹은 규칙을 만들지 않는다 (D-48)</li>
 *   <li>일부만 Y면 Y 코드 중 하나 이상에 해당해야 한다</li>
 *   <li>나이는 0~120이거나 빈 값이면 규칙 없음</li>
 *   <li>사용자구분에 개인·가구가 없으면 개인 추천에서 빼고(CORP), 소상공인 조건이 있으면 자영업 회원만 조건부(SMALL_BIZ) (D-49)</li>
 * </ul>
 *
 * <p>코드로 표현 안 되는 조건(수급 여부·거주 기간 등)은 원문 구조화(DATA-03)가 보완한다.
 */
public final class SupportConditionConverter {

    private SupportConditionConverter() {
    }

    /** 개인 추천 대상 구분 (benefits.target_scope). */
    public enum TargetScope {
        /** 개인·가구 대상. */
        PERSONAL,
        /** 소상공인 조건이 있음: 자영업 회원만 조건부, 나머지는 해당 없음 (D-49). */
        SMALL_BIZ,
        /** 법인·시설·단체 전용: 개인 추천에서 제외 (D-49). */
        CORP
    }

    /**
     * 변환 결과.
     *
     * @param rules       규칙 엔진용 규칙 (source = CODE, 묶음 0)
     * @param targetScope 개인 추천 대상 구분
     * @param warnings    값이 이상해서 규칙을 만들지 못한 항목 (예: 나이 하한이 상한보다 큼). 비어 있으면 정상
     */
    public record Result(List<Rule> rules, TargetScope targetScope, List<String> warnings) {
        public Result {
            rules = List.copyOf(rules);
            warnings = List.copyOf(warnings);
        }
    }

    static final Map<String, String> GENDER = Map.of("JA0101", "M", "JA0102", "F");
    static final List<String> JA01 = List.of("JA0101", "JA0102");
    static final List<String> JA02 = List.of("JA0201", "JA0202", "JA0203", "JA0204", "JA0205");
    static final List<String> JA03 = List.of("JA0301", "JA0302", "JA0303", "JA0313", "JA0314", "JA0315", "JA0316",
            "JA0317", "JA0318", "JA0319", "JA0320", "JA0326", "JA0327", "JA0328", "JA0329", "JA0330");
    static final List<String> JA04 = List.of("JA0401", "JA0402", "JA0403", "JA0404", "JA0411", "JA0412", "JA0413",
            "JA0414");
    static final List<String> JA11 = List.of("JA1101", "JA1102", "JA1103");
    static final List<String> JA12 = List.of("JA1201", "JA1202", "JA1299");
    static final List<String> JA21 = List.of("JA2101", "JA2102", "JA2103");
    static final List<String> JA22 = List.of("JA2201", "JA2202", "JA2203", "JA2299");
    static final String JA03_NONE = "JA0322";
    static final String JA04_NONE = "JA0410";
    static final String AGE_MIN = "JA0110";
    static final String AGE_MAX = "JA0111";
    static final int AGE_UNLIMITED_MAX = 120;

    /** 화면에 근거로 보여 줄 코드 이름 (supportConditions 명세). */
    static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        String[][] names = {
            {"JA0101", "남성"}, {"JA0102", "여성"},
            {"JA0201", "중위소득 0~50%"}, {"JA0202", "중위소득 51~75%"}, {"JA0203", "중위소득 76~100%"},
            {"JA0204", "중위소득 101~200%"}, {"JA0205", "중위소득 200% 초과"},
            {"JA0301", "예비부모·난임"}, {"JA0302", "임산부"}, {"JA0303", "출산·입양"},
            {"JA0313", "농업인"}, {"JA0314", "어업인"}, {"JA0315", "축산업인"}, {"JA0316", "임업인"},
            {"JA0317", "초등학생"}, {"JA0318", "중학생"}, {"JA0319", "고등학생"}, {"JA0320", "대학생·대학원생"},
            {"JA0326", "근로자·직장인"}, {"JA0327", "구직자·실업자"},
            {"JA0328", "장애인"}, {"JA0329", "국가보훈대상자"}, {"JA0330", "질병·질환자"},
            {"JA0401", "다문화가족"}, {"JA0402", "북한이탈주민"}, {"JA0403", "한부모·조손가족"}, {"JA0404", "1인가구"},
            {"JA0411", "다자녀가구"}, {"JA0412", "무주택세대"}, {"JA0413", "신규전입"}, {"JA0414", "확대가족"},
            {"JA1101", "예비창업자"}, {"JA1102", "영업중"}, {"JA1103", "생계곤란·폐업예정자"},
            {"JA1201", "음식점업"}, {"JA1202", "제조업"}, {"JA1299", "기타업종"},
        };
        for (String[] pair : names) {
            NAMES.put(pair[0], pair[1]);
        }
    }

    private static final Pattern MULTI_SEPARATOR = Pattern.compile("\\|\\||//");

    /**
     * @param conditions supportConditions 응답 한 건 (코드 → 값). 없는 코드·빈 값·"N"은 Y가 아닌 것으로 본다
     * @param userType   사용자구분 원문 (예: "개인//가구"). 비어 있으면 개인 대상으로 본다
     */
    public static Result convert(Map<String, String> conditions, String userType) {
        List<Rule> rules = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // JA01 성별
        List<String> genders = partialY(conditions, JA01, null);
        if (genders != null) {
            rules.add(rule("GENDER", Operator.IN, genders.stream().map(GENDER::get).toList(), genders));
        }

        age(conditions, warnings).ifPresent(rules::add);

        // JA02 중위소득 구간 (엔진이 구간 경계 ±5%p를 조건부로 처리, D-54)
        List<String> incomes = partialY(conditions, JA02, null);
        if (incomes != null) {
            rules.add(rule("INCOME_PCT", Operator.IN, incomes, incomes));
        }

        // JA03 개인 특성, JA04 가구 특성: 일반·민감 항목을 합쳐 판정 (D-48)
        List<String> traits = partialY(conditions, JA03, JA03_NONE);
        if (traits != null) {
            rules.add(rule("JA_GROUP:JA03", Operator.IN, traits, traits));
        }
        List<String> household = partialY(conditions, JA04, JA04_NONE);
        if (household != null) {
            rules.add(rule("JA_GROUP:JA04", Operator.IN, household, household));
        }

        // JA11·JA12 소상공인: Y가 하나라도 있으면 소상공인 대상 (전부 Y여도 '소상공인 전체'라는 뜻)
        List<String> smallBizStatus = yCodes(conditions, JA11);
        List<String> smallBizIndustry = yCodes(conditions, JA12);
        if (!smallBizStatus.isEmpty()) {
            rules.add(rule("JA_GROUP:JA11", Operator.IN, smallBizStatus, smallBizStatus));
        }
        if (!smallBizIndustry.isEmpty()) {
            rules.add(rule("JA_GROUP:JA12", Operator.IN, smallBizIndustry, smallBizIndustry));
        }

        TargetScope scope = scope(conditions, userType, !smallBizStatus.isEmpty() || !smallBizIndustry.isEmpty());
        return new Result(rules, scope, warnings);
    }

    /** 일부만 Y면 Y 코드 목록, 규칙이 필요 없으면(전부 Y·Y 없음·해당사항없음 Y) null. */
    static List<String> partialY(Map<String, String> conditions, List<String> codes, String noneCode) {
        if (noneCode != null && isY(conditions.get(noneCode))) {
            return null;
        }
        List<String> y = yCodes(conditions, codes);
        if (y.isEmpty() || y.size() == codes.size()) {
            return null;
        }
        return y;
    }

    private static List<String> yCodes(Map<String, String> conditions, List<String> codes) {
        return codes.stream().filter(code -> isY(conditions.get(code))).toList();
    }

    static boolean isY(String value) {
        return value != null && value.strip().equalsIgnoreCase("Y");
    }

    /** 나이: 0~120이거나 빈 값이면 규칙 없음. 상한이 120이면 '이상', 하한이 0이면 '이하'. */
    private static Optional<Rule> age(Map<String, String> conditions, List<String> warnings) {
        Integer low;
        Integer high;
        try {
            low = number(conditions.get(AGE_MIN));
            high = number(conditions.get(AGE_MAX));
        } catch (NumberFormatException e) {
            warnings.add("나이 값이 숫자가 아님: " + conditions.get(AGE_MIN) + "~" + conditions.get(AGE_MAX));
            return Optional.empty();
        }
        boolean noLow = low == null || low <= 0;
        boolean noHigh = high == null || high >= AGE_UNLIMITED_MAX;
        if (noLow && noHigh) {
            return Optional.empty();
        }
        if (!noLow && !noHigh && low > high) {
            warnings.add("나이 하한이 상한보다 큼: " + low + "~" + high);
            return Optional.empty();
        }
        Rule rule;
        if (noHigh) {
            rule = Rule.code("AGE", Operator.GTE, List.of(low.toString()), "지원조건: 만 " + low + "세 이상");
        } else if (noLow) {
            rule = Rule.code("AGE", Operator.LTE, List.of(high.toString()), "지원조건: 만 " + high + "세 이하");
        } else {
            rule = Rule.code("AGE", Operator.BETWEEN, List.of(low.toString(), high.toString()),
                    "지원조건: 만 " + low + "~" + high + "세");
        }
        return Optional.of(rule);
    }

    private static Integer number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value.strip());
    }

    /**
     * D-49. 사용자구분에 개인·가구가 없거나, 개인 특성(JA01~04)에 Y가 하나도 없고 기관 조건(JA21·22)만 있으면 CORP.
     * 그 외에 소상공인 조건이나 사용자구분 '소상공인'이 있으면 SMALL_BIZ.
     */
    static TargetScope scope(Map<String, String> conditions, String userType, boolean smallBizCodes) {
        List<String> types = userType == null ? List.of()
                : MULTI_SEPARATOR.splitAsStream(userType).map(String::strip).filter(s -> !s.isEmpty()).toList();
        boolean personal = types.isEmpty() || types.contains("개인") || types.contains("가구");
        boolean smallBiz = smallBizCodes || types.contains("소상공인");
        boolean personalCodes = !yCodes(conditions, JA01).isEmpty() || !yCodes(conditions, JA02).isEmpty()
                || !yCodes(conditions, JA03).isEmpty() || isY(conditions.get(JA03_NONE))
                || !yCodes(conditions, JA04).isEmpty() || isY(conditions.get(JA04_NONE));
        boolean orgCodes = !yCodes(conditions, JA21).isEmpty() || !yCodes(conditions, JA22).isEmpty();

        if (!personal) {
            return smallBiz ? TargetScope.SMALL_BIZ : TargetScope.CORP;
        }
        if (!personalCodes && orgCodes && !smallBiz) {
            return TargetScope.CORP;
        }
        return smallBizCodes ? TargetScope.SMALL_BIZ : TargetScope.PERSONAL;
    }

    private static Rule rule(String field, Operator op, List<String> values, List<String> codes) {
        String names = String.join(", ", codes.stream().map(c -> NAMES.getOrDefault(c, c)).toList());
        return Rule.code(field, op, values, "지원조건: " + names);
    }
}
