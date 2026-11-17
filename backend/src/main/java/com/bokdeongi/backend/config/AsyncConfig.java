package com.bokdeongi.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 사용 (프로필 저장 뒤 재판정 등). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
