package com.bokdeongi.backend.benefit.recommend;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Amount;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.ApplyBadge;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Check;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Item;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.NearMissInfo;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Period;
import com.bokdeongi.backend.eligibility.ConditionText;
import com.bokdeongi.backend.eligibility.label.LabelRuleCatalog;
import com.bokdeongi.backend.eligibility.store.JsonText;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 판정 결과 한 줄 → 화면 카드 (BEN-01·03·04·05). DB를 모르는 순수 변환이라 단위 테스트로 검증한다.
 *
 * <p>화면 문구(금액·기간·추천 이유·확인할 조건)를 서버에서 만들어 프론트가 같은 규칙을 다시 짜지 않게 한다.
 * 원래 값(금액·날짜·trace)도 함께 내려 프론트가 다르게 보여 줄 수 있다.
 */
public final class RecommendView {

    /** 판정 필드 → 화면 이름 */
    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("AGE", "나이"), Map.entry("GENDER", "성별"), Map.entry("REGION", "거주지"),
            Map.entry("INCOME_PCT", "소득"), Map.entry("HOUSEHOLD_SIZE", "가구원 수"),
            Map.entry("HOUSEHOLD_TYPE", "가구 유형"), Map.entry("STATUS", "현재 상황"),
            Map.entry("SENSITIVE", "특수 상황"), Map.entry("HOUSING", "주거 형태"), Map.entry("HAS_HOUSE", "주택 소유"),
            Map.entry("MARITAL", "혼인 상태"), Map.entry("CHILDREN_COUNT", "자녀 수"),
            Map.entry("YOUNGEST_CHILD_AGE", "막내 자녀 나이"), Map.entry("WELFARE", "기초생활수급"));

    /** 확인할 조건의 종류 → 화면에서 어떤 행동을 권할지 */
    public enum CheckType {
        /** 예/아니오/아직 몰라요로 답하면 바로 다시 판정 (BEN-04) */
        QUESTION,
        /** 프로필에서 입력하지 않은 항목 */
        PROFILE,
        /** 민감 항목: 민감정보 동의·입력이 필요 */
        SENSITIVE,
        /** 기준선 근처·'아직 몰라요' 답: 기관에 확인 */
        AGENCY,
        /** 규칙으로 못 바꾼 조건·해석 못 한 규칙: 공고 원문 확인 */
        SOURCE
    }

    static final int TOP_RECOMMENDED = 3;

    private RecommendView() {
    }

    /**
     * @param rankInTab 이 탭·정렬에서의 순번(0부터). 해당 탭 추천순 상위 3건에 '추천' 표시 (BEN-01)
     */
    static Item toItem(RecommendRow r, RecommendTab tab, RecommendSort sort, long rankInTab, LocalDate today) {
        List<Map<String, Object>> trace = traceOf(r.traceJson());
        boolean recommended = tab == RecommendTab.ELIGIBLE && sort == RecommendSort.RECOMMENDED
                && rankInTab < TOP_RECOMMENDED;
        boolean newlyEligible = "ELIGIBLE".equals(r.result()) && r.previousResult() != null
                && !"ELIGIBLE".equals(r.previousResult());
        return new Item(r.benefitId(), r.title(), r.serviceField(), r.summary(), r.supportType(), r.orgName(),
                r.regionName(), r.result(), recommended, newlyEligible, amount(r), period(r, today), applyBadge(r),
                tab == RecommendTab.ELIGIBLE ? eligibleReason(trace) : null,
                r.result().equals("CONDITIONAL") ? checks(trace) : List.of(),
                r.nearMiss() ? nearMiss(r, trace) : null, hashtags(r.hashtagsJson()),
                r.detailUrl() != null && !r.detailUrl().isBlank() ? r.detailUrl() : r.onlineApplyUrl(),
                r.bookmarked(), trace);
    }

    // ---------------------------------------------------------------- 금액 (DATA-05 값)

    public static Amount amount(RecommendRow r) {
        if (r.maxAmount() == null) {
            return null;
        }
        String prefix = switch (r.amountPeriod() == null ? "" : r.amountPeriod()) {
            case "MONTH" -> "월 최대 ";
            case "YEAR" -> "연 최대 ";
            default -> "최대 ";
        };
        return new Amount(r.maxAmount(), r.amountPeriod(), r.annualAmount(), prefix + won(r.maxAmount()));
    }

    /** 200000 → "20만원", 120000000 → "1억 2,000만원", 5000 → "5,000원" */
    static String won(long value) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.KOREA);
        long eok = value / 100_000_000L;
        long man = (value % 100_000_000L) / 10_000L;
        long rest = value % 10_000L;
        if (eok == 0 && man == 0) {
            return nf.format(value) + "원";
        }
        StringBuilder sb = new StringBuilder();
        if (eok > 0) {
            sb.append(nf.format(eok)).append("억");
        }
        if (man > 0) {
            sb.append(sb.isEmpty() ? "" : " ").append(nf.format(man)).append("만");
        }
        if (rest > 0) {
            sb.append(' ').append(nf.format(rest));
        }
        return sb.append("원").toString();
    }

    // ---------------------------------------------------------------- 신청 기간 (DATA-04 값)

    public static Period period(RecommendRow r, LocalDate today) {
        String status = r.dateStatus() == null ? "" : r.dateStatus();
        boolean month = "MONTH".equals(r.datePrecision());
        // 월까지만 아는 마감(D-66)은 말일로 저장돼 있어 D-day가 실제와 다를 수 있다 → D-day를 내지 않는다
        Integer dDay = r.applyEnd() == null || month ? null : (int) ChronoUnit.DAYS.between(today, r.applyEnd());
        String text;
        if ("ALWAYS".equals(status)) {
            text = "상시 신청";
        } else if ("NO_APPLICATION".equals(status)) {
            text = "신청 불필요";
        } else if (r.applyEnd() != null) {
            String end = month ? r.applyEnd().getYear() + "." + two(r.applyEnd().getMonthValue()) + " (월 단위)"
                    : r.applyEnd().getYear() + "." + two(r.applyEnd().getMonthValue()) + "."
                            + two(r.applyEnd().getDayOfMonth());
            text = "~ " + end + (dDay != null && dDay >= 0 ? " (D-" + dDay + ")" : "");
        } else if (r.recurringYearly()) {
            text = "매년 반복 (공고 확인)";
        } else {
            text = "공고 확인 필요";
        }
        return new Period(r.applyStart(), r.applyEnd(), status.isEmpty() ? null : status, r.datePrecision(),
                r.recurringYearly(), dDay, text);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }

    // ---------------------------------------------------------------- 신청 난이도 배지 (BEN-03)

    static ApplyBadge applyBadge(RecommendRow r) {
        boolean online = Boolean.TRUE.equals(r.onlineApply())
                || (r.onlineApply() == null && r.onlineApplyUrl() != null && !r.onlineApplyUrl().isBlank());
        String method = r.onlineApply() == null && (r.onlineApplyUrl() == null || r.onlineApplyUrl().isBlank())
                ? null : online ? "온라인 신청" : "방문 신청";
        Integer docs = r.selfDocCount();
        String docText = docs == null ? "서류 확인 필요" : docs == 0 ? "서류 없음" : "내가 낼 서류 " + docs + "개";
        return new ApplyBadge(online, method, docs, docText);
    }

    // ---------------------------------------------------------------- 판정 근거

    /** trace JSON → 화면용 목록. ruleId·group은 숫자로 바꾼다 */
    public static List<Map<String, Object>> traceOf(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        for (Map<String, Object> t : JsonText.objectList(json)) {
            Map<String, Object> m = new LinkedHashMap<>(t);
            m.computeIfPresent("ruleId", (k, v) -> Long.valueOf(v.toString()));
            m.computeIfPresent("group", (k, v) -> Integer.valueOf(v.toString()));
            out.add(m);
        }
        return out;
    }

    /** "나이·거주지·소득 조건을 충족해요" */
    static String eligibleReason(List<Map<String, Object>> trace) {
        Set<String> labels = new LinkedHashSet<>();
        for (Map<String, Object> t : trace) {
            if ("PASS".equals(t.get("outcome"))) {
                String label = label(String.valueOf(t.get("field")));
                if (label != null) {
                    labels.add(label);
                }
            }
        }
        if (labels.isEmpty()) {
            return "따로 정해진 자격 조건이 없는 혜택이에요";
        }
        return String.join("·", labels.stream().limit(4).toList()) + " 조건을 충족해요";
    }

    /** 조건부가 된 이유 = 확인할 조건 목록. 같은 항목은 한 번만 (BEN-04) */
    static List<Check> checks(List<Map<String, Object>> trace) {
        Map<String, Check> out = new LinkedHashMap<>();
        for (Map<String, Object> t : trace) {
            if (!"UNKNOWN".equals(t.get("outcome"))) {
                continue;
            }
            String field = String.valueOf(t.get("field"));
            String reason = String.valueOf(t.get("reason"));
            Object quoteValue = t.get("sourceQuote");
            String quote = quoteValue == null ? null : quoteValue.toString();
            Check c = check(field, reason, quote);
            out.putIfAbsent(c.type() + ":" + (c.questionKey() != null ? c.questionKey() : field + ":" + quote), c);
        }
        return List.copyOf(out.values());
    }

    public static Check check(String field, String reason, String quote) {
        String label = label(field);
        if (field.startsWith("ANSWER:") && "MISSING_VALUE".equals(reason)) {
            String key = field.substring("ANSWER:".length());
            // 검수 라벨에 다듬은 질문이 있으면 그것을, 없으면 근거 문구로 만든다 (D-93)
            String q = LabelRuleCatalog.standard().question(key).orElse(
                    quote == null || quote.isBlank() ? "이 조건에 해당하나요?" : "'" + quote + "'에 해당하나요?");
            return new Check(CheckType.QUESTION, field, key, q, quote);
        }
        return switch (reason) {
            case "MISSING_VALUE" -> new Check(CheckType.PROFILE, field, null,
                    (label == null ? "필요한 정보" : label) + " 정보를 입력하면 확인할 수 있어요", quote);
            case "SENSITIVE_UNKNOWN" -> new Check(CheckType.SENSITIVE, field, null,
                    "특수 상황(민감정보)에 동의하고 입력하면 확인할 수 있어요", quote);
            case "BOUNDARY" -> new Check(CheckType.AGENCY, field, null,
                    (label == null ? "조건" : label) + "이 기준선 근처라 기관 확인이 필요해요", quote);
            case "USER_UNSURE" -> new Check(CheckType.AGENCY, field, null, "기관에 확인해 보세요", quote);
            default -> new Check(CheckType.SOURCE, field, null, "공고 원문에서 확인이 필요해요", quote);
        };
    }

    static NearMissInfo nearMiss(RecommendRow r, List<Map<String, Object>> trace) {
        return nearMiss(r.nearMissReason(), trace);
    }

    /** 첫 미충족 조건 + 아깝게 탈락 이유 (신청 도우미 ① 자격 확인에서도 쓴다, ASST-02) */
    public static NearMissInfo nearMiss(String nearMissReason, List<Map<String, Object>> trace) {
        for (Map<String, Object> t : trace) {
            if ("FAIL".equals(t.get("outcome"))) {
                String field = String.valueOf(t.get("field"));
                return new NearMissInfo(nearMissReason, field, label(field),
                        ConditionText.expected(field, str(t.get("expected"))),
                        ConditionText.actual(field, str(t.get("actual"))), str(t.get("sourceQuote")));
            }
        }
        return new NearMissInfo(nearMissReason, null, null, null, null, null);
    }

    public static List<String> hashtags(String json) {
        try {
            List<String> tags = JsonText.stringList(json);
            return tags == null ? List.of() : tags;
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /** 판정 필드 → 화면 이름. 질문·원문 확인·묶음 코드는 null */
    public static String label(String field) {
        if (field.startsWith("JA_GROUP:")) {
            return "지원 대상";
        }
        return LABELS.get(field);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
