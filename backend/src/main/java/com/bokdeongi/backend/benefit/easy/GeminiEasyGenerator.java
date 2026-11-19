package com.bokdeongi.backend.benefit.easy;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 쉬운 말 LLM 호출 (EASY-01). Spring AI Gemini(spring-ai-starter-model-google-genai).
 *
 * <p>ChatModel 빈은 .env에 GEMINI_API_KEY와 AI_CHAT_MODEL=google-genai가 있을 때만 생긴다.
 * 없으면 generate가 예외를 던지고, EasyTextService는 저장 없이 FAILED로 끝낸다 (화면에는 원문만).
 *
 * <p>보내는 것은 공개된 공고문뿐이다. 회원 정보는 넣지 않는다 (D-92).
 */
@Component
public class GeminiEasyGenerator implements EasyGenerator {

    private final ObjectProvider<ChatModel> chatModel;
    private volatile ChatClient client;

    public GeminiEasyGenerator(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generate(String system, String user) {
        return client().prompt()
                .system(system)
                .user(user)
                .call()
                .content();
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
