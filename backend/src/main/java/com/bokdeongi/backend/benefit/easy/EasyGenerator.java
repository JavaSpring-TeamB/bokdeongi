package com.bokdeongi.backend.benefit.easy;

/**
 * 쉬운 말을 만드는 LLM 호출 (EASY-01). 구현은 Spring AI(Gemini)를 백엔드에 붙일 때 만든다.
 * 구현 빈이 없으면 EasyTextService는 LLM을 부르지 않고 FAILED를 돌려준다 (프론트는 원문만 보여 준다).
 */
public interface EasyGenerator {

    /**
     * @return 모델이 낸 JSON 글 {"who":..,"what":..,"how":..,"when":..,"text":..}
     * @throws RuntimeException 호출 실패 (한도 초과, 시간 초과 등)
     */
    String generate(String system, String user);
}
