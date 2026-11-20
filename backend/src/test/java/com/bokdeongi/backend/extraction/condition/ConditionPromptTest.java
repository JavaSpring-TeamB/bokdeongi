package com.bokdeongi.backend.extraction.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ConditionPromptTest {

    @Test
    void system은_data_lab에서_측정한_c5와_같다() throws Exception {
        Path measured = Path.of("..", "data-lab", "conditions", "prompt_c5_system.txt");
        Assumptions.assumeTrue(Files.exists(measured), "레포 루트 밖에서 실행");
        assertEquals(Files.readString(measured, StandardCharsets.UTF_8), ConditionPrompt.system());
    }

    @Test
    void user는_공고마다_머리줄과_칸을_반복한다() {
        ConditionSource a = ConditionFixtures.cheonan();
        String user = ConditionPrompt.user(List.of(a, a));
        assertTrue(user.startsWith("===== 공고 1 (service_id: 449000000119) =====\n[서비스명] 인플루엔자 예방접종\n"));
        assertTrue(user.contains("\n\n===== 공고 2 (service_id: 449000000119) =====\n"));
        assertTrue(user.contains("[선정기준] \n"));   // 빈 칸은 빈 문자열
        assertTrue(user.contains("[소관기관] 충청남도 천안시\n"));
    }
}
