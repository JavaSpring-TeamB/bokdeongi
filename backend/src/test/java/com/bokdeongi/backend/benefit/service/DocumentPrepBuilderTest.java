package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.IssuerGroup;
import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.MasterDoc;
import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.Prep;

class DocumentPrepBuilderTest {

    private static final MasterDoc 등본 = new MasterDoc(1L, "주민등록등본", List.of("등본", "주민등록표 등본"),
            "읍·면·동 행정복지센터", true, "https://www.gov.kr", "정부24", 90, null, false);
    private static final MasterDoc 초본 = new MasterDoc(2L, "주민등록초본", List.of("초본"),
            "읍·면·동 행정복지센터", true, "https://www.gov.kr", "정부24", 90, null, false);
    private static final MasterDoc 가족 = new MasterDoc(3L, "가족관계증명서", List.of(),
            "대법원", true, "https://efamily.scourt.go.kr", "전자가족관계등록시스템", 90, null, false);
    private static final MasterDoc 통장 = new MasterDoc(4L, "통장사본", List.of("통장 사본", "입금통장사본"),
            "본인 거래 은행", true, null, "은행 앱", null, null, false);
    private static final MasterDoc 진단서 = new MasterDoc(5L, "진단서", List.of(),
            "진료받은 병·의원", false, null, "병원", 90, null, false);
    private static final List<MasterDoc> MASTER = List.of(등본, 초본, 가족, 통장, 진단서);

    @Test
    void 이름이나_별칭이_같으면_그_서류() {
        assertEquals(등본, DocumentPrepBuilder.match("주민등록표 등본", MASTER));
        assertEquals(통장, DocumentPrepBuilder.match("통장 사본", MASTER));
    }

    @Test
    void 이름_안에_들어_있는_가장_긴_이름으로_맞춘다() {
        assertEquals(통장, DocumentPrepBuilder.match("청년 본인의 통장사본", MASTER));
        assertEquals(진단서, DocumentPrepBuilder.match("만성퇴행성관절염 진단서", MASTER));
    }

    @Test
    void 짧은_별칭은_같을_때만_맞춘다() {
        assertEquals(등본, DocumentPrepBuilder.match("등본", MASTER));
        assertNull(DocumentPrepBuilder.match("토지대장 등본", MASTER)); // "등본"이 들어 있어도 주민등록등본이 아님
    }

    @Test
    void 발급하는_곳별로_묶고_많은_곳이_위로() {
        Prep p = DocumentPrepBuilder.build("주민등록등본, 주민등록초본, 가족관계증명서, 통장사본, 사업계획서", MASTER);
        List<IssuerGroup> g = p.groups();
        assertEquals("정부24", g.get(0).place());
        assertEquals("https://www.gov.kr", g.get(0).url());
        assertEquals(2, g.get(0).items().size());
        assertEquals(List.of("대법원 전자가족관계등록시스템", "본인 거래 은행"), List.of(g.get(1).place(), g.get(2).place()));
        assertEquals(List.of("사업계획서"), p.forms());
        assertEquals(List.of(), p.unknown());
        assertEquals(5, p.total());
    }

    @Test
    void 같은_서류가_두_번_적혀도_한_번만() {
        Prep p = DocumentPrepBuilder.build("주민등록등본\n주민등록표 등본 1부", MASTER);
        assertEquals(1, p.total());
    }

    @Test
    void 신청서_동의서는_서식으로_뭉뚱그린_이름은_빼고_나머지는_모름() {
        Prep p = DocumentPrepBuilder.build("지원신청서, 개인정보 제공 동의서, 증빙서류, 임대차계약서", MASTER);
        assertEquals(List.of("지원신청서", "개인정보 제공 동의서"), p.forms());
        assertEquals(List.of("임대차계약서"), p.unknown());
    }
}
