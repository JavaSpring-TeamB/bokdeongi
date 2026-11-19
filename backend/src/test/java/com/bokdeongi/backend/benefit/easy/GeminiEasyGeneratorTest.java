package com.bokdeongi.backend.benefit.easy;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

class GeminiEasyGeneratorTest {

    @Test
    @SuppressWarnings("unchecked")
    void 키가_없어_ChatModel이_없으면_예외를_던진다() {
        ObjectProvider<ChatModel> none = mock(ObjectProvider.class);
        when(none.getIfAvailable()).thenReturn(null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new GeminiEasyGenerator(none).generate("system", "user"));
        assertTrue(e.getMessage().contains("GEMINI_API_KEY"));
    }
}
