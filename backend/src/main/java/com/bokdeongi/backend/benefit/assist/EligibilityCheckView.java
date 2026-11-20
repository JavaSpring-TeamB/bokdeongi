package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse.Condition;
import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse.Group;
import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse.Next;
import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse.Question;
import com.bokdeongi.backend.benefit.assist.EligibilityCheckResponse.Similar;
import com.bokdeongi.backend.benefit.recommend.RecommendResponse.Check;
import com.bokdeongi.backend.benefit.recommend.RecommendView;
import com.bokdeongi.backend.benefit.recommend.RecommendView.CheckType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 판정 trace → ① 자격 확인 화면 (ASST-02). DB를 모르는 순수 변환이다. */
public final class EligibilityCheckView {

    /** 규칙으로 다 못 바꿔 사람이 원문을 봐야 하는 이유 */
    static final Set<String> MANUAL_REASONS = Set.of("NEEDS_CHECK", "UNSUPPORTED_RULE", "LOW_CONFIDENCE");
    /** 진행은 되지만 기관 확인을 권하는 이유 */
    static final Set<String> AGENCY_REASONS = Set.of("BOUNDARY", "USER_UNSURE");

    private EligibilityCheckView() {
    }

    /** 공고 원문 중 화면에 보여 줄 것 */
    public record Source(String title, String serviceField, String targetText, String criteriaText) {
    }

    public static EligibilityCheckResponse build(long benefitId, String result, String nearMissReason,
            List<Map<String, Object>> trace, Source source, List<Similar> similar) {
        Map<Integer, List<Condition>> byGroup = new TreeMap<>();
        Map<String, Question> questions = new LinkedHashMap<>();
        boolean manual = false;
        boolean agency = false;
        boolean profile = false;
        int checks = 0;
        for (Map<String, Object> t : trace) {
            String field = str(t.get("field"));
            String outcome = str(t.get("outcome"));
            String reason = str(t.get("reason"));
            String quote = str(t.get("sourceQuote"));
            int group = t.get("group") == null ? 0 : Integer.parseInt(t.get("group").toString());
            String status = switch (outcome == null ? "" : outcome) {
                case "PASS" -> "MET";
                case "FAIL" -> "NOT_MET";
                default -> "CHECK";
            };
            CheckType type = null;
            String guide = null;
            if ("CHECK".equals(status)) {
                checks++;
                Check c = RecommendView.check(field, reason == null ? "" : reason, quote);
                type = c.type();
                guide = c.text();
                if (c.type() == CheckType.QUESTION) {
                    questions.putIfAbsent(c.questionKey(), new Question(c.questionKey(), c.text(), quote));
                }
                manual |= MANUAL_REASONS.contains(reason);
                agency |= AGENCY_REASONS.contains(reason);
                profile |= c.type() == CheckType.PROFILE || c.type() == CheckType.SENSITIVE;
            }
            byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(new Condition(field, label(field), status,
                    str(t.get("expected")), str(t.get("actual")), quote, type, guide));
        }

        List<Group> groups = new ArrayList<>();
        byGroup.forEach((g, conditions) -> groups.add(new Group(g, g > 0,
                g == 0 ? "공통 조건" : "다음 중 하나 (" + g + ")", groupStatus(conditions), List.copyOf(conditions))));

        boolean ineligible = "INELIGIBLE".equals(result);
        String summary = switch (result == null ? "" : result) {
            case "ELIGIBLE" -> "모든 조건을 충족해요";
            case "INELIGIBLE" -> "충족하지 않는 조건이 있어요";
            default -> checks == 0 ? "확인이 필요한 조건이 있어요" : "확인할 조건이 " + checks + "개 있어요";
        };
        String message;
        if (ineligible) {
            message = "조건을 충족하지 않아 신청을 이어갈 수 없어요. 아래 비슷한 혜택을 확인해 보세요";
        } else if (manual) {
            message = "자동으로 확인하지 못한 조건이 있어요. 공고 원문을 읽고 '직접 확인했어요'를 눌러 주세요";
        } else if (!questions.isEmpty()) {
            message = "아래 질문에 답하면 바로 다시 확인해요";
        } else if (profile) {
            message = "프로필에 빠진 정보를 입력하면 다시 확인해요";
        } else if (agency) {
            message = "기준선 근처이거나 확인이 필요한 조건이 있어요. 신청 전에 기관에 문의해 보세요";
        } else if ("ELIGIBLE".equals(result)) {
            message = "다음 단계에서 낼 서류를 준비해요";
        } else {
            message = "확인이 필요한 조건을 살펴본 뒤 다음 단계로 넘어가세요";
        }
        String sourceText = manual ? join(source.targetText(), source.criteriaText()) : null;
        Next next = new Next(!ineligible, !ineligible && manual, !ineligible && agency, message, sourceText,
                ineligible ? RecommendView.nearMiss(nearMissReason, trace) : null,
                ineligible ? List.copyOf(similar) : List.of());
        // 미충족이면 질문에 답해도 결과가 바뀌지 않으므로 묻지 않는다
        return new EligibilityCheckResponse(benefitId, source.title(), result, summary, List.copyOf(groups),
                ineligible ? List.of() : List.copyOf(questions.values()), next);
    }

    /** 묶음 안: 미충족 > 확인 필요 > 충족 (D-53) */
    static String groupStatus(List<Condition> conditions) {
        boolean check = false;
        for (Condition c : conditions) {
            if ("NOT_MET".equals(c.status())) {
                return "NOT_MET";
            }
            check |= "CHECK".equals(c.status());
        }
        return check ? "CHECK" : "MET";
    }

    private static String label(String field) {
        if (field == null) {
            return null;
        }
        if (field.startsWith("ANSWER:")) {
            return "추가 질문";
        }
        if ("UNPARSED".equals(field) || "RULES".equals(field)) {
            return "공고 원문";
        }
        return RecommendView.label(field);
    }

    private static String join(String a, String b) {
        List<String> parts = new ArrayList<>();
        if (a != null && !a.isBlank()) {
            parts.add("[지원대상]\n" + a.strip());
        }
        if (b != null && !b.isBlank()) {
            parts.add("[선정기준]\n" + b.strip());
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
