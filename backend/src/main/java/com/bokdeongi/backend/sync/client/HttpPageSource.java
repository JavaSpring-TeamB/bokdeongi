package com.bokdeongi.backend.sync.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털 공공서비스(혜택) 정보 API (api.odcloud.kr/api/gov24/v3)를 실제로 부른다.
 *
 * <p>인증키는 .env의 DATA_GO_KR_SERVICE_KEY (data-lab 수집 스크립트와 같은 키). Decoding 키를 넣는 게 원칙이고,
 * Encoding 키를 넣어도 한 번 풀어서 쓴다. 키는 로그·예외 메시지에 남기지 않는다.
 */
@Component
public class HttpPageSource implements PageSource {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String serviceKey;
    private final String baseUrl;
    private final HttpClient http;

    public HttpPageSource(@Value("${app.gov24.service-key:}") String serviceKey,
            @Value("${app.gov24.base-url:https://api.odcloud.kr/api/gov24/v3}") String baseUrl) {
        String key = serviceKey == null ? "" : serviceKey.strip();
        this.serviceKey = key.contains("%") ? URLDecoder.decode(key, StandardCharsets.UTF_8) : key;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public boolean configured() {
        return !serviceKey.isEmpty();
    }

    @Override
    public String fetch(String endpoint, int page, int perPage) {
        if (!configured()) {
            throw new ApiCallException(
                    "DATA_GO_KR_SERVICE_KEY가 비어 있습니다. backend/.env에 공공데이터포털 인증키(Decoding)를 넣어 주세요.", false);
        }
        URI uri = URI.create(baseUrl + "/" + endpoint + "?page=" + page + "&perPage=" + perPage
                + "&serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiCallException(endpoint + " " + page + "페이지 네트워크 오류: " + e.getClass().getSimpleName(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiCallException(endpoint + " 호출이 중단되었습니다", false, e);
        }
        int code = response.statusCode();
        if (code == 200) {
            return response.body();
        }
        String body = response.body() == null ? "" : response.body();
        String brief = body.length() > 200 ? body.substring(0, 200) : body;
        if (code == 401 || code == 403) {
            throw new ApiCallException(endpoint + " 인증 오류(HTTP " + code + "): 인증키를 확인하세요. " + brief, false);
        }
        throw new ApiCallException(endpoint + " " + page + "페이지 HTTP " + code + ": " + brief, true);
    }
}
