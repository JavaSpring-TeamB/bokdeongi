package com.bokdeongi.backend.sync.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Gov24ClientTest {

    /** total건을 perPage씩 나눠 주는 가짜 API. maxPerPage보다 많이 달라고 하면 maxPerPage만 준다 */
    static class FakeApi implements PageSource {
        final int total;
        final int maxPerPage;
        final List<String> calls = new ArrayList<>();
        int failFirst;
        boolean authError;

        FakeApi(int total, int maxPerPage) {
            this.total = total;
            this.maxPerPage = maxPerPage;
        }

        @Override
        public String fetch(String endpoint, int page, int perPage) {
            calls.add(endpoint + ":" + page + ":" + perPage);
            if (authError) {
                throw new ApiCallException("인증 오류", false);
            }
            if (failFirst > 0) {
                failFirst--;
                throw new ApiCallException("HTTP 503", true);
            }
            int size = Math.min(perPage, maxPerPage);
            int from = (page - 1) * size;
            StringBuilder sb = new StringBuilder("{\"page\":" + page + ",\"perPage\":" + perPage + ",\"totalCount\":"
                    + total + ",\"data\":[");
            for (int i = from; i < Math.min(total, from + size); i++) {
                sb.append(i == from ? "" : ",").append("{\"서비스ID\":\"S").append(i).append("\",\"서비스명\":\"혜택 ")
                        .append(i).append("\"}");
            }
            return sb.append("]}").toString();
        }
    }

    private static Gov24Client client(FakeApi api) {
        Gov24Client c = new Gov24Client(api);
        c.sleeper(ms -> { });
        return c;
    }

    @Test
    void 전체_페이지를_받고_호출_수를_센다() {
        FakeApi api = new FakeApi(1100, 1000);
        CallBudget budget = new CallBudget(100);
        List<Map<String, Object>> all = client(api).fetchAll("serviceList", budget);
        assertEquals(1100, all.size());
        assertEquals("S1099", all.get(1099).get("서비스ID"));
        assertEquals(3, budget.used());
    }

    @Test
    void 일시_장애는_다시_시도하고_재시도도_호출_수에_넣는다() {
        FakeApi api = new FakeApi(10, 1000);
        api.failFirst = 2;
        CallBudget budget = new CallBudget(100);
        assertEquals(10, client(api).fetchAll("serviceDetail", budget).size());
        assertEquals(3, budget.used());
    }

    @Test
    void 세_번_모두_실패하면_멈춘다() {
        FakeApi api = new FakeApi(10, 1000);
        api.failFirst = 5;
        ApiCallException e = assertThrows(ApiCallException.class,
                () -> client(api).fetchAll("serviceDetail", new CallBudget(100)));
        assertTrue(e.getMessage().contains("3번 시도"));
    }

    @Test
    void 인증_오류는_다시_시도하지_않는다() {
        FakeApi api = new FakeApi(10, 1000);
        api.authError = true;
        assertThrows(ApiCallException.class, () -> client(api).fetchAll("serviceList", new CallBudget(100)));
        assertEquals(1, api.calls.size());
    }

    @Test
    void 한_번에_주는_건수가_적으면_그_크기로_다시_받는다() {
        FakeApi api = new FakeApi(250, 100);
        List<Map<String, Object>> all = client(api).fetchAll("serviceList", new CallBudget(100));
        assertEquals(250, all.size());
        assertEquals("serviceList:1:500", api.calls.get(0));
        assertEquals("serviceList:1:100", api.calls.get(1));
    }

    @Test
    void 호출_한도에_닿으면_멈춘다() {
        FakeApi api = new FakeApi(1100, 1000);
        assertThrows(ApiCallException.class, () -> client(api).fetchAll("serviceList", new CallBudget(2)));
        assertEquals(2, api.calls.size());
    }

    @Test
    void 응답이_JSON이_아니면_재시도_대상() {
        ApiCallException e = assertThrows(ApiCallException.class,
                () -> Gov24Client.parse("serviceList", 1, "<html>Service Unavailable</html>"));
        assertTrue(e.retryable());
    }
}
