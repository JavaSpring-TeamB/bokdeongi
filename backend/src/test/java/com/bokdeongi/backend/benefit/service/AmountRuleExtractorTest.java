package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.AmountRuleExtractor.Period;
import com.bokdeongi.backend.benefit.service.AmountRuleExtractor.Result;

class AmountRuleExtractorTest {

    /** data-lab/validation/cases.json의 numbers와 같은 값. cases.json이 바뀌면 같이 고친다 */
    private static final String[][] NUMBER_CASES = {
            {"월 최대 20만원", "200000"},
            {"지원금액 : 200천원(1가구당)", "200000,1"},
            {"월 19,250원", "19250"},
            {"2,000만원", "20000000"},
            {"1억 2천만원", "100000000,20000000,120000000"},
            {"1만 5천원", "10000,5000,15000"},
            {"20만, 30만", "200000,300000"},
            {"10만 20만원", "100000,200000"},
            {"5천 1만원", "5000,10000"},
            {"기준 중위소득 60% 이하", "60"},
            {"만 19〜34세", "19,34"},
            {"1,2순위", "1,2"},
            {"0.5%", "0.5"},
            {"2026.04.27~2026.05.29", "2026,4,27,5,29"},
            {"매년 01-01~01-31", "1,31"},
            {"7천5백만원", "75000000"},
            {"2백5십만원", "2500000"},
            {"1천2백만 원", "12000000"},
            {"3천5백원", "3500"},
            {"1억 7천5백만원", "100000000,75000000,175000000"},
            {"", ""},
    };

    @Test
    void 숫자_읽기는_검증_모듈과_같다() {
        for (String[] c : NUMBER_CASES) {
            Set<BigDecimal> expected = new TreeSet<>();
            for (String v : c[1].split(",")) {
                if (!v.isEmpty()) {
                    expected.add(new BigDecimal(v));
                }
            }
            assertEquals(expected, new TreeSet<>(AmountRuleExtractor.extractNumbers(c[0])), c[0]);
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
}
