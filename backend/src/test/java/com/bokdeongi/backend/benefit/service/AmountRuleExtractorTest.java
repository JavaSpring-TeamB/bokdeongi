package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.AmountRuleExtractor.Period;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.benefit.service.AmountRuleExtractor.Result;

class AmountRuleExtractorTest {

    /**
     * 금액 숫자 읽기의 기준은 검증 모듈(ExtractionValidator.extractNumbers) 하나다.
     * 규칙이 고른 금액은 검증 모듈이 같은 글에서 읽은 숫자 안에 있어야 한다.
     */
    @Test
    void 고른_금액은_검증_모듈이_읽은_숫자_안에_있다() {
        String[][] cases = {
                {"월 최대 20만원", "200000"},
                {"지원금액 : 200천원(1가구당)", "200000"},
                {"월 19,250원", "19250"},
                {"7천5백만원 이내", "75000000"},
                {"1인당 2백5십만원", "2500000"},
                {"1천2백만 원", "12000000"},
                {"3천5백원", "3500"},
                {"최대 1억 7천5백만원", "175000000"},
        };
        for (String[] c : cases) {
            Long picked = AmountRuleExtractor.extract(c[0], "현금").maxAmount();
            assertEquals(Long.valueOf(c[1]), picked, c[0]);
            assertTrue(ExtractionValidator.extractNumbers(c[0]).contains(ExtractionValidator.canon(c[1])), c[0]);
        }
    }

    @Test
    void 월_금액은_연간으로_환산한다() {
        Result r = AmountRuleExtractor.extract("ㅇ (지원내용) 월 최대 20만원 임차료 지원(최대 24개월)", "현금");
        assertEquals(200_000L, r.maxAmount());
        assertEquals(Period.MONTH, r.period());
        assertEquals(2_400_000L, r.annualAmount());
    }

    @Test
    void 대상별로_다르면_큰_값() {
        Result r = AmountRuleExtractor.extract("- 지원 금액 : (초) 1인당 20만원, (중고) 1인당 30만원", "기타");
        assertEquals(300_000L, r.maxAmount());
        assertEquals(Period.ONCE, r.period());
    }

    @Test
    void 연_횟수를_곱한다() {
        Result r = AmountRuleExtractor.extract("○ 일년에 두 번, 설, 추석 명절에 가구당 6만원, 차상위계층은 가구당 5만원 지급", "현금");
        assertEquals(60_000L, r.maxAmount());
        assertEquals(120_000L, r.annualAmount());
    }

    @Test
    void 단가와_사업비와_자격기준은_금액이_아니다() {
        assertNull(AmountRuleExtractor.extract("○ 일반농가 ha당 35만원, 친환경농가 ha당 50만원", "현금").maxAmount());
        Result r = AmountRuleExtractor.extract("○ 지원단가 : 10만원/톤\n○ 지원한도 : 농가당 300만원 이내(30톤)", "현물");
        assertEquals(3_000_000L, r.maxAmount());
        r = AmountRuleExtractor.extract("ㅇ (지원내용) 월 최대 20만원\nㅇ (총사업비) 2,997억원(국비 1,367억원)", "현금");
        assertEquals(200_000L, r.maxAmount());
        r = AmountRuleExtractor.extract("② 2025년도 매출액 1억 4백만원 미만\nㅇ (지원금액) 업체당 지원금 30만원 이내 1회 지급", "현금");
        assertEquals(300_000L, r.maxAmount());
        r = AmountRuleExtractor.extract("- 본인부담금 3만원 이상인 경우\n- 1인당 10만원 한도 내에서 보상비 지급", "현금");
        assertEquals(100_000L, r.maxAmount());
    }

    @Test
    void 현물은_금액_표시가_있을_때만() {
        assertNull(AmountRuleExtractor.extract("○ 양로시설 입소 어르신에게 명절맞이 위문물품 지원\n   - 70만원 / 연 2회", "현물").maxAmount());
        assertEquals(200_000L, AmountRuleExtractor.extract("지원금액 : 200천원(1가구당)", "현물").maxAmount());
    }

    @Test
    void 보험과_대출은_연간_금액을_비운다() {
        Result r = AmountRuleExtractor.extract("- 자연재해 상해사망(2,000만원)\n- 스쿨존 부상치료비(1,000만원)", "현금(보험)");
        assertEquals(20_000_000L, r.maxAmount());
        assertNull(r.annualAmount());
        assertNull(AmountRuleExtractor.extract("- 최고 7천만 원 이내", "현금(융자)").annualAmount());
    }

    @Test
    void 이어_쓴_한국식_금액을_읽는다() {
        assertEquals(75_000_000L, AmountRuleExtractor.extract("- 최고 7천만 원 이내(단, 다자녀 가구는 7천5백만 원 이내)", "현금(융자)").maxAmount());
        assertEquals(2_500_000L, AmountRuleExtractor.extract("1인당 2백5십만원 지원", "현금").maxAmount());
        assertEquals(175_000_000L, AmountRuleExtractor.extract("주택구입자금 최대 1억 7천5백만원 대출", "현금(융자)").maxAmount());
        assertEquals(15_000_000L, AmountRuleExtractor.extract("최대 1천5백만원", "현금").maxAmount());
    }

    @Test
    void 연간_횟수는_교육_같은_활동_횟수와_헷갈리지_않는다() {
        assertEquals(300_000L, AmountRuleExtractor.extract("○ 1인당 30만원 지원\n○ 역량강화 교육 연 2회 실시", "현금").annualAmount());
        assertEquals(600_000L, AmountRuleExtractor.extract("○ 1인당 30만원 지원\n○ 지급시기: 연 2회(설, 추석)", "현금").annualAmount());
        assertEquals(120_000L, AmountRuleExtractor.extract("○ 명절위문금 6만원(연 2회)", "현금").annualAmount());
    }

    @Test
    void 범위의_주기는_범위_앞에서_찾는다() {
        Result r = AmountRuleExtractor.extract("○ 아동 1인당 매월 300천원~500천원 연령별 차등 지급", "현금");
        assertEquals(500_000L, r.maxAmount());
        assertEquals(Period.MONTH, r.period());
        assertEquals(6_000_000L, r.annualAmount());
    }

    @Test
    void 금액_뒤_빗금이_단위가_아니면_단가가_아니다() {
        assertEquals(200_000L, AmountRuleExtractor.extract("- 지원금액 : 임신부 1인 최대 20만원 / 서비스 이용 회별 초과비용은 본인부담", "현금").maxAmount());
        assertNull(AmountRuleExtractor.extract("지원단가 60만원/ha", "현금").maxAmount());
    }

    @Test
    void 같은_줄_앞에_ha당이_있으면_단가다() {
        assertNull(AmountRuleExtractor.extract("- 지원기준 : ha당 논 25만원, 채소특작 55만원, 과실 60만원", "현금").maxAmount());
    }

    @Test
    void 본인부담금은_지원_금액이_아니다() {
        assertNull(AmountRuleExtractor.extract("○ 본인부담금\n- 기준중위소득 75% 이하 본인부담금 월 30,000원", "서비스(돌봄)").maxAmount());
        assertEquals(500_000L, AmountRuleExtractor.extract("의료비 본인부담금 최대 50만원 지원", "현금").maxAmount());
    }
}
