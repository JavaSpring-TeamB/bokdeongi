package com.bokdeongi.backend.benefit.easy;

/**
 * 쉬운 말을 만드는 LLM 호출 (EASY-01). 구현은 GeminiEasyGenerator (Spring AI Gemini).
 * 호출이 실패하거나 LLM이 꺼져 있으면 예외를 던지고, EasyTextService는 저장 없이 FAILED를 돌려준다.
 */
public interface EasyGenerator {

    /**
     * @return 모델이 낸 JSON 글 {"who":..,"what":..,"how":..,"when":..,"text":..}
     * @throws RuntimeException 호출 실패 (한도 초과, 시간 초과 등)
     */
    String generate(String system, String user);
}
