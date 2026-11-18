package com.bokdeongi.backend.benefit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse.Choice;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse.Group;
import com.bokdeongi.backend.benefit.dto.response.DocumentPrepResponse.Item;
import com.bokdeongi.backend.benefit.dto.response.MissingDocumentResponse;
import com.bokdeongi.backend.benefit.service.MissingDocumentService.DocStatus;

class MissingDocumentServiceTest {

    private static Item item(long id, String name, boolean online, List<Choice> orDocs) {
        return new Item(id, name, name, "발급처", online, name + " 발급 방법", 90, null, false, orDocs);
    }

    private static final Item 등본 = item(1, "주민등록등본", true, List.of());
    private static final Item 초본 = item(2, "주민등록초본", true, List.of());
    private static final Item 수급 = item(6, "국민기초생활수급자 증명서", true,
            List.of(new Choice(7L, "차상위계층 확인서", "읍·면·동 행정복지센터", true, "https://www.gov.kr", "정부24")));
    private static final Item 진단서 = item(5, "진단서", false, List.of());

    private static final DocumentPrepResponse PREP = new DocumentPrepResponse(List.of(
            new Group("정부24", "https://www.gov.kr", List.of(등본, 초본, 수급)),
            new Group("진료받은 병·의원", null, List.of(진단서))),
            List.of("지원신청서"), List.of("임대차계약서"), List.of(), 6);

    @Test
    void 상태가_없으면_전부_부족() {
        MissingDocumentResponse r = MissingDocumentService.missing(PREP, null);
        assertFalse(r.allReady());
        assertEquals(List.of(3, 1), r.groups().stream().map(g -> g.documents().size()).toList());
        assertEquals("MISSING", r.groups().get(0).documents().get(0).result());
        assertEquals(MissingDocumentService.NOT_YET, r.groups().get(0).documents().get(0).reason());
        assertEquals(List.of("지원신청서"), r.forms());
        assertEquals(List.of("임대차계약서"), r.unknown());
    }

    @Test
    void 준비된_서류는_빼고_INVALID는_이유와_함께() {
        MissingDocumentResponse r = MissingDocumentService.missing(PREP, Map.of(
                1L, new DocStatus("READY", null),
                2L, new DocStatus("INVALID", "발급일이 90일을 넘었어요.")));
        List<MissingDocumentResponse.Doc> docs = r.groups().get(0).documents();
        assertEquals(List.of("주민등록초본", "국민기초생활수급자 증명서"), docs.stream().map(MissingDocumentResponse.Doc::name).toList());
        assertEquals("INVALID", docs.get(0).result());
        assertEquals("발급일이 90일을 넘었어요.", docs.get(0).reason());
        assertEquals("주민등록초본 발급 방법", docs.get(0).howTo());
        assertEquals(List.of("차상위계층 확인서"), docs.get(1).orDocs());
    }

    @Test
    void 또는의_한쪽이_준비되면_준비된_것() {
        MissingDocumentResponse r = MissingDocumentService.missing(PREP, Map.of(7L, new DocStatus("READY", null)));
        assertEquals(List.of("주민등록등본", "주민등록초본"),
                r.groups().get(0).documents().stream().map(MissingDocumentResponse.Doc::name).toList());
    }

    @Test
    void 온라인이_아니면_방문_안내() {
        MissingDocumentResponse r = MissingDocumentService.missing(PREP, null);
        assertTrue(r.groups().get(0).online());
        assertNull(r.groups().get(0).visitTip());
        assertFalse(r.groups().get(1).online());
        assertEquals(MissingDocumentService.VISIT_TIP, r.groups().get(1).visitTip());
    }

    @Test
    void 모두_준비되거나_필요_없으면_allReady() {
        MissingDocumentResponse r = MissingDocumentService.missing(PREP, Map.of(
                1L, new DocStatus("READY", null), 2L, new DocStatus("READY", null),
                6L, new DocStatus("READY", null), 5L, new DocStatus("NOT_REQUIRED", null)));
        assertTrue(r.allReady());
        assertEquals(List.of(), r.groups());
    }
}
