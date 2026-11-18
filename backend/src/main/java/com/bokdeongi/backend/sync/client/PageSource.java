package com.bokdeongi.backend.sync.client;

/**
 * 공공서비스 API 한 페이지를 받아 응답 본문(JSON 글자)을 돌려준다. 테스트에서는 가짜로 바꿔 끼운다.
 */
public interface PageSource {

    /**
     * @param endpoint serviceList / serviceDetail / supportConditions
     * @param page     1부터
     * @param perPage  한 번에 받을 건수
     * @return 응답 본문
     * @throws ApiCallException 한 번 시도해서 실패. 재시도는 부르는 쪽({@link Gov24Client})이 한다
     */
    String fetch(String endpoint, int page, int perPage);
}
