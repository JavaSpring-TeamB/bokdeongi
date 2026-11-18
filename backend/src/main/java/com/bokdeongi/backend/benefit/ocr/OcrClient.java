package com.bokdeongi.backend.benefit.ocr;

import com.bokdeongi.backend.eligibility.store.JsonText;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 로컬 OCR 서버(ocr-server, C) 호출 (ASST-03).
 *
 * <p>OCR 서버는 같은 컴퓨터(127.0.0.1)에서만 열리는 내부 서버라 서류 이미지가 밖으로 나가지 않는다 (AGENTS.md 3번).
 * 주소는 .env의 OCR_SERVER_URL (기본 http://127.0.0.1:8001).
 *
 * <p>응답 JSON은 JsonText로 읽는다 (Jackson 2·3이 함께 있어 버전에 묶이지 않으려고, Benefit JSON 칸과 같은 방식).
 */
@Component
public class OcrClient {

    /** 처음 부를 때 한국어 모델을 내려받아서 오래 걸린다 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    @Autowired
    public OcrClient(@Value("${OCR_SERVER_URL:http://127.0.0.1:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** 테스트용 (MockRestServiceServer) */
    OcrClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 서류 검토 (POST /check).
     *
     * @param data         파일 내용 (JPG·PNG·PDF, 10MB 이하)
     * @param filename     원래 파일 이름 (OCR 서버는 확장자를 믿지 않고 앞 바이트로 형식을 본다)
     * @param expectedType RESIDENT_REGISTER / HEALTH_INSURANCE_PAYMENT / ENROLLMENT / INCOME_AMOUNT
     * @param maxAgeDays   발급 후 유효 일수. null이면 OCR 서버 기본값
     * @throws DocumentCheckException 파일이 크거나 형식이 다르면 4xx, OCR 서버가 꺼져 있거나 실패하면 AI_UNAVAILABLE(502)
     */
    public OcrCheckResult check(byte[] data, String filename, String expectedType, Integer maxAgeDays) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "upload" : filename;
            }
        });
        if (expectedType != null) {
            form.add("expected_type", expectedType);
        }
        if (maxAgeDays != null) {
            form.add("max_age_days", String.valueOf(maxAgeDays));
        }
        String body;
        try {
            body = restClient.post()
                    .uri("/check")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw fromError(response.getStatusCode().value(), text);
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw DocumentCheckException.unavailable(); // OCR 서버가 꺼져 있거나 시간 초과
        }
        return parse(body);
    }

    /** /check 응답 JSON → 결과 */
    static OcrCheckResult parse(String json) {
        Object v;
        try {
            v = JsonText.parse(json);
        } catch (IllegalArgumentException e) {
            throw DocumentCheckException.unavailable();
        }
        if (!(v instanceof Map<?, ?> m) || m.get("result") == null) {
            throw DocumentCheckException.unavailable();
        }
        return new OcrCheckResult(str(m.get("result")), str(m.get("detectedType")), str(m.get("issueDate")),
                strings(m.get("foundFields")), strings(m.get("missingFields")), str(m.get("reason")));
    }

    /**
     * OCR 서버 에러 → 우리 에러 코드.
     * FastAPI 에러 본문: {"detail": {"code": "FILE_TOO_LARGE", "message": "..."}}
     */
    static DocumentCheckException fromError(int status, String body) {
        String code = null;
        String message = null;
        try {
            if (JsonText.parse(body) instanceof Map<?, ?> m && m.get("detail") instanceof Map<?, ?> d) {
                code = str(d.get("code"));
                message = str(d.get("message"));
            }
        } catch (IllegalArgumentException ignored) {
            // 본문이 JSON이 아니면 상태 코드로만 판단
        }
        if (status == 413 || "FILE_TOO_LARGE".equals(code)) {
            return DocumentCheckException.fileTooLarge();
        }
        if (status == 415 || "UNSUPPORTED_FORMAT".equals(code)) {
            return DocumentCheckException.unsupportedFormat();
        }
        if (status == 400 && message != null) {
            return DocumentCheckException.invalidInput(message);
        }
        return DocumentCheckException.unavailable();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x != null) {
                    out.add(x.toString());
                }
            }
        }
        return out;
    }

}
