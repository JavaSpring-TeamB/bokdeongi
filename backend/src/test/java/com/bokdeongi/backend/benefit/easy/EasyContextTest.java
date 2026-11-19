package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 키 없이(spring.ai.model.chat=none) 서버가 뜨고, 쉬운 말 LLM 구현은 등록되되 ChatModel은 없다 */
@SpringBootTest
class EasyContextTest {

    @Autowired
    private ObjectProvider<ChatModel> chatModel;

    @Autowired
    private EasyGenerator generator;

    @Test
    void 키가_없어도_서버가_뜨고_ChatModel은_없다() {
        assertNull(chatModel.getIfAvailable());
        assertEquals(GeminiEasyGenerator.class, generator.getClass());
    }
}
