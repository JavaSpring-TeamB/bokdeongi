package com.bokdeongi.backend.benefit.easy;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 쉬운 말 미리 만들기 실행 시점. .env의 EASY_SERVICE_IDS(쉼표로 구분한 서비스ID)가 있을 때만 서버 시작 직후 한 번 돈다.
 * 기본은 비어 있어서 팀원이 서버를 띄워도 LLM을 부르지 않는다. 돌린 뒤에는 값을 지운다 (DATA-03 ConditionRunner와 같은 방식).
 */
@Component
public class EasyRunner {

    private static final Logger log = LoggerFactory.getLogger(EasyRunner.class);

    private final EasyBatch batch;
    private final List<String> serviceIds;

    public EasyRunner(EasyBatch batch, @Value("${app.easy.service-ids:}") String serviceIds) {
        this.batch = batch;
        this.serviceIds = Arrays.stream(serviceIds.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (serviceIds.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                batch.run(serviceIds);
            } catch (RuntimeException e) {
                log.error("쉬운 말 미리 만들기 중 오류", e);
            }
        }, "easy-pregenerate");
        t.setDaemon(true);
        t.start();
    }
}
