package com.bokdeongi.backend.benefit.assist;

import com.bokdeongi.backend.benefit.recommend.RecommendResponse.NearMissInfo;
import com.bokdeongi.backend.benefit.recommend.RecommendView.CheckType;

import java.util.List;

/**
 * 신청 도우미 ① 자격 확인 (ASST-02). 판정은 규칙 엔진(ELIG-01) 결과 그대로이고, 여기서는 조건별로 보여 줄 모양만 만든다.
 *
 * @param result    ELIGIBLE / CONDITIONAL / INELIGIBLE
 * @param summary   한 줄 요약 ("확인할 조건이 2개 있어요")
 * @param groups    조건 묶음. 0번은 공통 조건(모두 충족), 1번 이상은 '다음 중 하나' (D-62)
 * @param questions 부족한 정보 중 예/아니오로 바로 답할 수 있는 것. 답은 PUT /api/me/answers (BEN-04·PROF-06)
 * @param next      다음 단계(② 서류 준비)로 갈 수 있는지와 안내
 */
public record EligibilityCheckResponse(long benefitId, String title, String result, String summary,
        List<Group> groups, List<Question> questions, Next next) {

    /**
     * @param alternative true면 '다음 중 하나' 묶음 (1번 이상)
     * @param status      묶음 판정 MET / CHECK / NOT_MET (묶음 안 조건이 모두 충족이면 MET, 하나라도 미충족이면 NOT_MET)
     */
    public record Group(int group, boolean alternative, String title, String status, List<Condition> conditions) {
    }

    /**
     * 조건 하나 (원칙 2: 근거 원문 문구를 함께 보여 준다).
     *
     * @param status    MET 충족 / CHECK 확인 필요 / NOT_MET 미충족
     * @param checkType 확인 필요일 때 무엇을 하면 되는지 (QUESTION·PROFILE·SENSITIVE·AGENCY·SOURCE), 아니면 null
     * @param guide     확인 필요일 때 안내 문구, 아니면 null
     */
    public record Condition(String field, String label, String status, String expected, String actual,
            String sourceQuote, CheckType checkType, String guide) {
    }

    /** @param key PUT /api/me/answers의 answers 키 */
    public record Question(String key, String text, String sourceQuote) {
    }

    /**
     * @param canProceed            미충족이면 false: ②로 넘어가지 못한다
     * @param manualConfirmRequired 조건을 규칙으로 다 못 바꾼 공고. 원문을 보고 '직접 확인했어요'를 눌러야 진행
     * @param agencyCheckRecommended 기준선 근처·'아직 몰라요' 답이 있어 기관 문의를 권한다 (진행은 허용)
     * @param message               화면 안내 문구
     * @param sourceText            manualConfirmRequired일 때 보여 줄 원문 (지원대상·선정기준)
     * @param nearMiss              미충족일 때 첫 미충족 조건과 아깝게 탈락 이유
     * @param similar               미충족일 때 같은 분야에서 '해당'인 혜택 (마감 빠른 순 3건)
     */
    public record Next(boolean canProceed, boolean manualConfirmRequired, boolean agencyCheckRecommended,
            String message, String sourceText, NearMissInfo nearMiss, List<Similar> similar) {
    }

    public record Similar(long benefitId, String title, String period) {
    }
}
