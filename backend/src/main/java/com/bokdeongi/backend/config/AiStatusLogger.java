package com.bokdeongi.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버를 켤 때 AI(Gemini)가 켜졌는지 로그에 한 줄 남긴다. 꺼져 있으면 쉬운 말·공고문 질문·쉬운 말 번역기가 502 AI_UNAVAILABLE을 낸다.
 * 키 값은 남기지 않는다.
 */
@Component
public class AiStatusLogger {

    private static final Logger log = LoggerFactory.getLogger(AiStatusLogger.class);

    private final ObjectProvider<ChatModel> chatModel;
    private final String model;

    public AiStatusLogger(ObjectProvider<ChatModel> chatModel,
                          @Value("${spring.ai.google.genai.chat.model:-}") String model) {
        this.chatModel = chatModel;
        this.model = model;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (chatModel.getIfAvailable() == null) {
            log.warn("AI 꺼짐: .env에 GEMINI_API_KEY와 AI_CHAT_MODEL=google-genai가 없어 AI 기능(쉬운 말·공고문 질문·쉬운 말 번역기)은 502 AI_UNAVAILABLE을 낸다");
        } else {
            log.info("AI 켜짐: 모델 {}", model);
        }
    }
}
