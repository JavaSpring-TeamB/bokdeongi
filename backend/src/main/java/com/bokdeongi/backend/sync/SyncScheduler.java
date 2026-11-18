package com.bokdeongi.backend.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 동기화 실행 시점 (DATA-01).
 *
 * <ul>
 *   <li>매일 새벽(기본 04:40, 한국 시각): app.sync.enabled=true일 때만. 공용 DB에 쓰므로 배포 서버 한 곳에서만 켠다</li>
 *   <li>서버 시작 직후 한 번: app.sync.run-on-startup=true일 때 (처음 적재·수동 실행용). 시작을 막지 않게 따로 돈다</li>
 * </ul>
 * 둘 다 기본값은 꺼짐이라, 팀원이 로컬에서 서버를 띄워도 API를 부르거나 공용 DB를 바꾸지 않는다.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final BenefitSyncService sync;
    private final boolean enabled;
    private final boolean runOnStartup;

    public SyncScheduler(BenefitSyncService sync, @Value("${app.sync.enabled:false}") boolean enabled,
            @Value("${app.sync.run-on-startup:false}") boolean runOnStartup) {
        this.sync = sync;
        this.enabled = enabled;
        this.runOnStartup = runOnStartup;
    }

    @Scheduled(cron = "${app.sync.cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void daily() {
        if (enabled) {
            runSafely("정기");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (runOnStartup) {
            Thread t = new Thread(() -> runSafely("시작 시"), "benefit-sync-startup");
            t.setDaemon(true);
            t.start();
        }
    }

    private void runSafely(String when) {
        try {
            BenefitSyncService.Report r = sync.run();
            log.info("{} 동기화 결과: {}", when, r);
        } catch (RuntimeException e) {
            log.error("{} 동기화 중 오류", when, e);
        }
    }
}
