package com.bokdeongi.backend.sync.client;

/**
 * 이번 동기화에서 쓸 수 있는 호출 수. 개발 계정 하루 한도(10,000)에서 오늘 이미 쓴 만큼을 빼고,
 * 한 번 실행에 쓰는 최대치(app.sync.max-calls)도 넘지 않게 한다. 재시도도 한 번으로 센다.
 */
public class CallBudget {

    private final int limit;
    private int used;

    public CallBudget(int limit) {
        this.limit = Math.max(0, limit);
    }

    public void take() {
        if (used >= limit) {
            throw new ApiCallException("호출 한도에 도달했습니다 (이번 실행 " + limit + "회). 다음 실행에서 다시 받습니다.",
                    false);
        }
        used++;
    }

    public int used() {
        return used;
    }

    public int limit() {
        return limit;
    }
}
