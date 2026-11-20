package com.bokdeongi.backend.extraction.condition;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 판정 조건 추출 (DATA-03) Gemini 호출. 모델·생각 수준은 application.properties의 spring.ai.google.genai.* (D-97).
 *
 * <p>ChatModel 빈은 .env에 GEMINI_API_KEY와 AI_CHAT_MODEL=google-genai가 있을 때만 생긴다.
 * 보내는 것은 공개된 공고문뿐이다. 회원 정보는 넣지 않는다 (D-92).
 */
@Component
public class GeminiConditionExtractor implements ConditionExtractor {

    private final ObjectProvider<ChatModel> chatModel;
    private volatile ChatClient client;

    public GeminiConditionExtractor(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ConditionBatch extract(String system, String user) {
        ConditionBatch batch = client().prompt()
                .system(system)
                .user(user)
                .call()
                .entity(ConditionBatch.class);
        if (batch == null) {
            throw new IllegalStateException("조건 추출 응답이 비어 있음");
        }
        return batch;
    }

    private ChatClient client() {
        ChatClient c = client;
        if (c == null) {
            ChatModel model = chatModel.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException("LLM이 꺼져 있음 (.env에 GEMINI_API_KEY, AI_CHAT_MODEL=google-genai 없음)");
            }
            c = ChatClient.create(model);
            client = c;
        }
        return c;
    }
}
