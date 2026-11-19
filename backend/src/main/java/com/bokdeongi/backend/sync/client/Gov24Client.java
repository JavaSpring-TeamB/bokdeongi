package com.bokdeongi.backend.sync.client;

import com.bokdeongi.backend.eligibility.store.JsonText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 공공서비스 API 한 종류(serviceList·serviceDetail·supportConditions)를 처음부터 끝까지 받는다.
 *
 * <p>응답 모양: {"page":1,"perPage":500,"totalCount":10942,"currentCount":500,"matchCount":10942,"data":[{...}]}.
 * 한 페이지 실패는 최대 3번까지 다시 시도하고(재시도도 호출 수에 넣는다), 그래도 안 되면 예외로 멈춘다.
 * 1페이지가 요청보다 적게 오면 API 한 번 최대 건수가 그만큼이라고 보고 그 크기로 처음부터 다시 받는다.
 */
@Component
public class Gov24Client {

    private static final Logger log = LoggerFactory.getLogger(Gov24Client.class);

    public static final String SERVICE_LIST = "serviceList";
    public static final String SERVICE_DETAIL = "serviceDetail";
    public static final String SUPPORT_CONDITIONS = "supportConditions";

    static final int PER_PAGE = 500;
    static final int MAX_ATTEMPTS = 3;

    /** 재시도 사이 대기. 테스트에서는 기다리지 않게 바꾼다 */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final PageSource source;
    private Sleeper sleeper = Thread::sleep;

    public Gov24Client(PageSource source) {
        this.source = source;
    }

    void sleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /** 한 종류의 전체 레코드. 키는 API 원문 이름("서비스ID", "서비스명" ...) */
    public List<Map<String, Object>> fetchAll(String endpoint, CallBudget budget) {
        return fetchAll(endpoint, budget, PER_PAGE);
    }

    List<Map<String, Object>> fetchAll(String endpoint, CallBudget budget, int perPage) {
        Page first = page(endpoint, 1, perPage, budget);
        if (first.data.size() < Math.min(perPage, first.total) && !first.data.isEmpty()) {
            log.warn("{} 1페이지에 {}건만 왔습니다(요청 {}건). 그 크기로 다시 받습니다.", endpoint, first.data.size(), perPage);
            return fetchAll(endpoint, budget, first.data.size());
        }
        List<Map<String, Object>> all = new ArrayList<>(first.data);
        int pages = Math.max(1, (int) Math.ceil(first.total / (double) perPage));
        for (int p = 2; p <= pages; p++) {
            all.addAll(page(endpoint, p, perPage, budget).data);
        }
        log.info("{} {}건 받음 (전체 {}건, {}페이지)", endpoint, all.size(), first.total, pages);
        return all;
    }

    private Page page(String endpoint, int page, int perPage, CallBudget budget) {
        ApiCallException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            budget.take();
            try {
                return parse(endpoint, page, source.fetch(endpoint, page, perPage));
            } catch (ApiCallException e) {
                if (!e.retryable()) {
                    throw e;
                }
                last = e;
                log.warn("{} (시도 {}/{})", e.getMessage(), attempt, MAX_ATTEMPTS);
                try {
                    sleeper.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ApiCallException(endpoint + " 호출이 중단되었습니다", false, ie);
                }
            }
        }
        throw new ApiCallException(endpoint + " " + page + "페이지를 " + MAX_ATTEMPTS + "번 시도했지만 받지 못했습니다: "
                + last.getMessage(), false, last);
    }

    record Page(int total, List<Map<String, Object>> data) {
    }

    /** 응답 형식이 다르면 재시도 대상(일시 장애로 HTML 오류 페이지가 오는 경우가 있다) */
    @SuppressWarnings("unchecked")
    static Page parse(String endpoint, int page, String body) {
        Object root;
        try {
            root = JsonText.parse(body);
        } catch (IllegalArgumentException e) {
            throw new ApiCallException(endpoint + " " + page + "페이지 응답이 JSON이 아닙니다", true, e);
        }
        if (!(root instanceof Map<?, ?> map) || !(map.get("data") instanceof List<?> list)) {
            throw new ApiCallException(endpoint + " " + page + "페이지 응답에 data 목록이 없습니다", true);
        }
        int total = number(map.get("totalCount"), number(map.get("matchCount"), list.size()));
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                data.add((Map<String, Object>) m);
            }
        }
        return new Page(total, data);
    }

    private static int number(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
