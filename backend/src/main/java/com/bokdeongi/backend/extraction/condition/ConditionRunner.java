package com.bokdeongi.backend.extraction.condition;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 조건 추출 실행 시점. .env의 CONDITIONS_SERVICE_IDS(쉼표로 구분한 서비스ID)가 있을 때만 서버 시작 직후 한 번 돈다.
 * 기본은 비어 있어서, 팀원이 서버를 띄워도 LLM을 부르거나 공용 DB 규칙을 바꾸지 않는다. 돌린 뒤에는 값을 지운다.
 */
@Component
public class ConditionRunner {

    private static final Logger log = LoggerFactory.getLogger(ConditionRunner.class);

    private final ConditionExtractionService service;
    private final List<String> serviceIds;

    public ConditionRunner(ConditionExtractionService service,
            @Value("${app.conditions.service-ids:}") String serviceIds) {
        this.service = service;
        this.serviceIds = Arrays.stream(serviceIds.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (serviceIds.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                service.run(serviceIds);
            } catch (RuntimeException e) {
                log.error("조건 추출 중 오류", e);
            }
        }, "condition-extraction");
        t.setDaemon(true);
        t.start();
    }
}
