package com.bokdeongi.backend.sync.client;

/**
 * 공공서비스 API 호출 실패. 재시도 후에도 실패하면 이번 동기화는 멈추고 DB는 바꾸지 않는다
 * (서비스는 마지막 성공 데이터로 동작하고, 다음 실행에서 다시 받는다).
 */
public class ApiCallException extends RuntimeException {

    /** 일시 장애(네트워크, 5xx, 429)라 다시 시도할 만한지. 인증 오류·한도 도달은 false */
    private final boolean retryable;

    public ApiCallException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ApiCallException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
