package com.bokdeongi.backend.extraction.condition;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * DATA-03 판정 조건 추출 프롬프트. system 글은 C가 측정한 data-lab/conditions/prompt_c5_system.txt를 그대로 복사한
 * resources/prompts/conditions-c5-system.txt를 쓴다 (두 파일이 같은지 테스트가 본다).
 * 프롬프트를 바꾸면 data-lab에서 측정한 뒤 복사하고 VERSION을 올린다.
 */
public final class ConditionPrompt {

    public static final String VERSION = "c5";
    static final String SYSTEM_RESOURCE = "/prompts/conditions-c5-system.txt";

    private static volatile String system;

    private ConditionPrompt() {
    }

    public static String system() {
        String s = system;
        if (s == null) {
            try (InputStream in = ConditionPrompt.class.getResourceAsStream(SYSTEM_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("프롬프트 파일 없음: " + SYSTEM_RESOURCE);
                }
                s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            system = s;
        }
        return s;
    }

    /** user 메시지 (data-lab/conditions/prompt_c3_user.txt 모양, c4·c5도 같음). 공고마다 블록을 반복한다 */
    public static String user(List<ConditionSource> sources) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (ConditionSource s : sources) {
            if (n > 1) {
                sb.append('\n');
            }
            sb.append("===== 공고 ").append(n++).append(" (service_id: ").append(s.serviceId()).append(") =====\n")
                    .append("[서비스명] ").append(text(s.title())).append('\n')
                    .append("[소관기관] ").append(text(s.orgName())).append('\n')
                    .append("[지원대상] ").append(text(s.targetText())).append('\n')
                    .append("[선정기준] ").append(text(s.criteriaText())).append('\n')
                    .append("[지원내용] ").append(text(s.contentText())).append('\n')
                    .append("[신청방법] ").append(text(s.applyMethodText())).append('\n');
        }
        return sb.toString();
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }
}
