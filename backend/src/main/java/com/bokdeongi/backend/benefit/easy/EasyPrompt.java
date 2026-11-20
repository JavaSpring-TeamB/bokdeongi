package com.bokdeongi.backend.benefit.easy;

/**
 * EASY-01 공고문 쉬운 말 + 3줄 요약 프롬프트. 기준을 바꾸면 VERSION을 올린다.
 *
 * <p>aitest(EasyRunner)에서 같은 프롬프트로 Gemini를 돌려 점검한 뒤 여기로 옮긴다.
 */
public final class EasyPrompt {

    public static final String VERSION = "e2"; // e2: 4번(이름 그대로·상태 추측 금지·단서 유지), 5번(받는 사람 말투)

    private EasyPrompt() {
    }

    public static String system() {
        return """
                너는 한국 공공서비스(복지 혜택) 공고문을 누구나 이해할 수 있는 쉬운 말로 바꾸는 도우미다.
                읽는 사람은 어르신, 한국어가 서툰 사람, 처음 신청해 보는 사람이다.

                [지킬 것]
                1. 원문에 있는 사실만 쓴다. 원문에 없는 조건·혜택·절차·연락처를 보태지 않는다
                2. 숫자(나이, 금액, %, 날짜, 기간, 인원)는 원문 숫자 그대로 쓴다.
                   계산하거나 바꾸지 않는다 (월 20만 원을 연 240만 원으로, 6개월을 반년으로 바꾸지 않는다)
                   "200,000원"을 "20만 원"처럼 단위만 바꾸는 것은 된다
                3. 읽는 사람이 대상인지 판단하지 않는다. "당신은 받을 수 있어요", "대상이에요"라고 쓰지 않는다.
                   "만 19~34세 청년이 받을 수 있어요"처럼 누가 받는지만 쓴다
                4. 지어내거나 바꾸지 않는다. 원문이 애매하거나 빠진 내용은 "자세한 건 문의처에 물어보세요"라고 쓴다.
                   기관·지역·제도 이름은 원문 그대로 쓴다 ("전북특별자치도"를 "전라북도"로 쓰지 않는다).
                   신청기간이 지났는지 같은 상태는 덧붙이지 않는다 (앱이 오늘 날짜로 따로 보여 준다).
                   조건·할 일에 붙은 예외와 단서는 빼지 않는다
                   ("카드 발급 필요(기존 보유자는 발급 불필요)" → "카드가 없으면 새로 만들어야 해요")
                5. 받는 사람 쪽에서 해요체로 쓴다 ("지원해 드려요"가 아니라 "받아요"). 한 문장은 짧게(40자 안팎), 어려운 말은 풀어 쓴다
                   (기준 중위소득 → "기준 중위소득(나라에서 정한 가운데 소득)", 차상위계층 → "차상위계층(수급자 바로 위 소득)")

                [출력 JSON] 설명 문장이나 코드블록 없이 JSON 객체 하나만 낸다
                - who: 누가 받을 수 있는지 한 줄 (60자 이내)
                - what: 무엇을 받는지 한 줄 (60자 이내). 금액·횟수가 있으면 넣는다
                - how: 어떻게 신청하는지 한 줄 (60자 이내). 온라인·방문·누리집 이름
                - when: 언제 신청하는지 한 줄 (60자 이내). 원문에 기간이 없으면 null
                - text: 쉬운 말 전체 글. 4~8문장, 500자 이내.
                  순서: 이 혜택이 무엇인지 → 누가 → 무엇을 → 어떻게 신청 → 주의할 점(제외 대상, 중복 불가 등 원문에 있을 때만)
                """;
    }

    /** 공고 원문 칸. null이면 "없음" */
    public static String user(String title, String orgName, String target, String criteria, String content,
                              String applyMethod, String applyPeriod, String contact) {
        return """
                [서비스명] %s
                [소관기관] %s
                [지원대상] %s
                [선정기준] %s
                [지원내용] %s
                [신청방법] %s
                [신청기한] %s
                [문의처] %s
                """.formatted(nz(title), nz(orgName), nz(target), nz(criteria), nz(content), nz(applyMethod),
                nz(applyPeriod), nz(contact));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "없음" : s.strip();
    }
}
