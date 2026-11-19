package com.bokdeongi.backend.benefit.ask;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 공고문 질문 LLM 호출 (BEN-08). 쉬운 말(GeminiEasyGenerator)과 같은 Spring AI Gemini.
 * ChatModel 빈은 .env에 GEMINI_API_KEY와 AI_CHAT_MODEL=google-genai가 있을 때만 생긴다.
 */
@Component
public class AskGenerator {

    private final ObjectProvider<ChatModel> chatModel;
    private volatile ChatClient client;

    public AskGenerator(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    /** LLM이 켜져 있는지 (키가 있는지) */
    public boolean available() {
        return chatModel.getIfAvailable() != null;
    }

    /**
     * @return 모델이 낸 JSON 글
     * @throws RuntimeException LLM이 꺼져 있거나 호출 실패
     */
    public String generate(String system, String user) {
        ChatClient c = client;
        if (c == null) {
            ChatModel model = chatModel.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException("LLM이 꺼져 있음 (.env에 GEMINI_API_KEY, AI_CHAT_MODEL=google-genai 없음)");
            }
            c = ChatClient.create(model);
            client = c;
        }
        return c.prompt().system(system).user(user).call().content();
    }
}
