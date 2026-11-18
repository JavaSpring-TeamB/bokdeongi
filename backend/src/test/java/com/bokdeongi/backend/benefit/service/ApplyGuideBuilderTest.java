package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.ApplyGuideBuilder.Guide;
import com.bokdeongi.backend.benefit.service.ApplyGuideBuilder.Input;
import com.bokdeongi.backend.benefit.service.ApplyGuideBuilder.Step;

class ApplyGuideBuilderTest {

    private static Guide build(String text) {
        return ApplyGuideBuilder.build(new Input(text, null, null));
    }

    @Test
    void 머리줄은_단계가_아니라_아래_줄의_신청_방법이_된다() {
        Guide g = build("○ 방문 신청\n - 주민센터 : 주소지 관할 읍면동 주민센터에 방문 신청\n - 시군구 : 관할 구청 방문");
        assertEquals(ApplyGuideBuilder.VISIT, g.method());
        assertEquals(List.of(
                new Step("VISIT", "주민센터 : 주소지 관할 읍면동 주민센터에 방문 신청"),
                new Step("VISIT", "시군구 : 관할 구청 방문")), g.steps());
    }

    @Test
    void 온라인과_방문이_다_있으면_둘_다() {
        Guide g = build("○ 온라인 신청\n - 복지로(www.bokjiro.go.kr) 접속 후 신청\n○ 방문 신청\n - 주소지 행정복지센터");
        assertEquals(ApplyGuideBuilder.ONLINE_OR_VISIT, g.method());
        assertEquals("https://www.bokjiro.go.kr", g.applyUrl());
        assertEquals("복지로", g.siteName());
        assertEquals(List.of("ONLINE", "VISIT"), g.steps().stream().map(Step::method).toList());
    }

    @Test
    void 화살표와_번호로_단계를_나눈다() {
        assertEquals(List.of("홈페이지 가입", "카드번호 등록", "편의점에서 할인등록"),
                build("홈페이지 가입 -> 카드번호 등록 → 편의점에서 할인등록").steps().stream().map(Step::text).toList());
        assertEquals(List.of("온라인 접수( 정부24 )", "이메일 접수", "방문 접수(세종시청 4층)"),
                build("① 온라인 접수( 정부24 )\n② 이메일 접수\n③ 방문 접수(세종시청 4층)").steps().stream()
                        .map(Step::text).toList());
    }

    @Test
    void 참고_줄은_단계에서_뺀다() {
        Guide g = build("○ 방문 신청\n - 관내 지정 위탁의료기관에 전화 후 방문\n  *지정 위탁의료기관: 보건소 홈페이지에서 확인\n※ 신분증 지참");
        assertEquals(1, g.steps().size());
        assertEquals(List.of("지정 위탁의료기관: 보건소 홈페이지에서 확인", "신분증 지참"), g.notes());
    }

    @Test
    void 신청_주소는_온라인_주소_칸이_먼저() {
        Guide g = ApplyGuideBuilder.build(new Input("정부24에서 신청", "https://www.example.go.kr/apply", null));
        assertEquals("https://www.example.go.kr/apply", g.applyUrl());
        assertNull(g.siteName());
    }

    @Test
    void 주소가_없으면_원문의_누리집_이름으로() {
        Guide g = build("정부24 또는 주민센터 방문 신청");
        assertEquals("https://www.gov.kr", g.applyUrl());
        assertEquals("정부24", g.siteName());
    }

    @Test
    void 앞을_뺀_주소도_꺼내고_이메일_도메인은_빼고() {
        assertEquals("https://mindup.kicece.or.kr", ApplyGuideBuilder.url("홈페이지 직접 신청(mindup.kicece.or.kr)"));
        assertEquals("https://www.gov.kr", ApplyGuideBuilder.url("정부24(www.gov.kr)."));
        assertNull(ApplyGuideBuilder.url("담당자 이메일(abc@korea.kr)로 제출"));
    }

    @Test
    void 홈페이지_직접_신청은_방문이_아니다() {
        assertEquals(ApplyGuideBuilder.ONLINE, build("○ 온라인 신청: 홈페이지 직접 신청(mindup.kicece.or.kr)").method());
    }

    @Test
    void 전화번호의_빗금은_나누지_않는다() {
        assertEquals(List.of("순창군청 장애인복지팀 063)650-1252 / 650-1206"),
                build("순창군청 장애인복지팀 063)650-1252 / 650-1206").steps().stream().map(Step::text).toList());
    }

    @Test
    void 머리줄_한_줄뿐이면_그것을_단계로() {
        Guide g = build("방문신청");
        assertEquals(ApplyGuideBuilder.VISIT, g.method());
        assertEquals(List.of(new Step("VISIT", "방문신청")), g.steps());
    }

    @Test
    void 신청_방법을_알_수_없으면_그_밖() {
        Guide g = build("○ 개인 신청절차 없음");
        assertEquals(ApplyGuideBuilder.OTHER, g.method());
        assertEquals(List.of(new Step(null, "개인 신청절차 없음")), g.steps());
    }

    @Test
    void 단계는_최대_10개() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            lines.add("- " + i + "번째 할 일");
        }
        assertEquals(ApplyGuideBuilder.MAX_STEPS, build(String.join("\n", lines)).steps().size());
    }
}
