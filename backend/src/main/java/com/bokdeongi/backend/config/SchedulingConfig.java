package com.bokdeongi.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 작업 사용 (DATA-01 일일 동기화). 실제 실행 여부는 app.sync.enabled로 정한다 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
