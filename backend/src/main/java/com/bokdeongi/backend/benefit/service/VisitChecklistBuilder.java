package com.bokdeongi.backend.benefit.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 방문 신청 준비물 체크리스트 (ASST-06). LLM을 부르지 않는다.
 *
 * - 방문 신청인지: 신청방법 원문에 "방문", "내방", "창구", "직접 제출" 같은 말이 있으면
 * - 준비물: 신분증 + 내가 낼 서류(구비서류 칸, DocumentRuleExtractor). 공무원 확인 서류는 넣지 않는다
 * - 접수처: 접수기관 칸 → 없으면 신청방법의 방문 부분에서 꺼낸 곳 → 그것도 없으면 소관기관 (placeFromOrg = true)
 * - 문의처: 문의처 칸 "부서/전화||부서/전화"를 나눈다
 */
public final class VisitChecklistBuilder {

    /** 공고 원문 칸 (benefits 테이블의 같은 이름 칸) */
    public record Input(String applyMethodText, String requiredDocsText, String receivingOrg,
                        String orgName, String contact) {
    }

    public record Contact(String name, String phone) {
    }

    /**
     * @param visitApply  방문 신청이 가능한 공고인지. false면 이 화면을 보여 주지 않는다
     * @param items       준비물 (신분증이 맨 앞)
     * @param places      접수처 (최대 3곳)
     * @param placeFromOrg 접수처를 원문에서 못 찾아 소관기관으로 대신했는지
     * @param contacts    문의처
     */
    public record Checklist(boolean visitApply, List<String> items, List<String> places, boolean placeFromOrg,
                            List<Contact> contacts) {
    }

    private static final String ID_CARD = "신분증";
    private static final int MAX_PLACES = 3;

    private static final Pattern VISIT = Pattern.compile("방문|내방|창구|직접\\s*(?:제출|신청|접수)");
    /** 방문이 아닌 신청 방법 머리말: 여기서 방문 부분이 끝난다 */
    private static final Pattern OTHER_METHOD = Pattern.compile(
            "^\\s*(?:[○●◎■□▶·\\-*]|\\d+[.)]|[①-⑳])?\\s*\\(?(?:온라인|인터넷|모바일|우편|팩스|이메일|전화|유선)");
    /** "주민센터 : 관할 읍면동 주민센터 방문 신청" 의 오른쪽 */
    private static final Pattern LABEL_VALUE = Pattern.compile("^[^:：]{1,12}[:：]\\s*(.+)$");
    /** "방문 접수(세종시청 청년정책담당관, 4층 408호)" 의 괄호 안 */
    private static final Pattern VISIT_PAREN = Pattern.compile("(?:방문|내방)\\s*(?:신청|접수)?\\s*\\(([^)]{2,40})\\)");
    private static final Pattern BULLET = Pattern.compile("^[\\s○●◎■□▶·\\-*※]+|^\\s*(?:\\d+[.)]|[①-⑳])\\s*");
    /** 접수처로 인정하는 장소 낱말 */
    private static final Pattern PLACE_WORD = Pattern.compile(
            "센터|사무소|사무실|시청|구청|군청|도청|교육청|지청|청사|보건소|학교|유치원|어린이집|기관|병원|의원|약국|은행|농협|축협|수협|신협|조합|우체국"
            + "|지사|공단|식당|복지관|회관|도서관|담당관|시설|대행점|판매점|읍면|읍·면|동사무소|면사무소"
            + "|(?:팀|과)(?=$|[\\s,(])|[소관원점실처]$");
    /** 장소가 아니라 서류 안내인 줄 */
    private static final Pattern DOC_LINE = Pattern.compile("구비\\s*서류|제출\\s*서류|필요\\s*서류");
    /** 장소가 아닌 안내 줄의 라벨: "- 신청기간 : 정기신청(3월 중)" */
    private static final Pattern SKIP_LABEL = Pattern.compile("^[^:：]*(?:기간|시기|일정|일시|대상|자격|지급|선정)[^:：]*[:：]");
    /** 장소 설명이 아닌 괄호: "(신분증 지참)", "(방문 전 문의전화 권장)" */
    private static final Pattern NOTE_PAREN = Pattern.compile("\\([^)]*(?:지참|신분증|문의|권장|통장|사본|불가)[^)]*\\)");
    /** 앞에 붙은 절차 말: "사업신청서를 작성하여 해당 읍면사무소" */
    private static final Pattern LEADING_CLAUSE = Pattern.compile("^(?:.*?(?:작성하여|작성 후|하여|한 후|후)\\s+(?:\\d+\\s*(?:년|개월|일)\\s*이내\\s+)?|(?:신청\\s*)?기간\\s*내에?\\s+)");
    /** 뒤에 붙은 조사·동작 말 (여러 번 지운다) */
    private static final Pattern TRAILING = Pattern.compile(
            "(?:\\s*직접|\\s*(?:직접\\s*)?(?:서류\\s*접수|서류\\s*제출|방문|내방|문의|신청서?|접수|제출|및|또는)|에서|에게|에|으로|로|[\\s,.→\\-])$");

    private VisitChecklistBuilder() {
    }

    public static Checklist build(Input in) {
        String method = nz(in.applyMethodText());
        boolean visit = VISIT.matcher(method).find();

        List<String> items = new ArrayList<>();
        items.add(ID_CARD);
        for (String doc : DocumentRuleExtractor.extract(in.requiredDocsText())) {
            if (!doc.replaceAll("\\s", "").startsWith(ID_CARD) && !items.contains(doc)) {
                items.add(doc);
            }
        }

        List<String> places = new ArrayList<>();
        boolean fromOrg = false;
        if (!nz(in.receivingOrg()).isBlank()) {
            places.add(in.receivingOrg().strip());
        } else if (visit) {
            places.addAll(placesFrom(method));
        }
        if (places.isEmpty() && !nz(in.orgName()).isBlank()) {
            places.add(in.orgName().strip());
            fromOrg = true;
        }
        return new Checklist(visit, items, places, fromOrg, contacts(in.contact()));
    }

    /** 신청방법에서 방문 부분만 보고 접수처를 꺼낸다 */
    static List<String> placesFrom(String method) {
        Set<String> out = new LinkedHashSet<>();
        boolean inVisit = false;
        for (String raw : method.split("\\r?\\n|\\s/\\s")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (VISIT.matcher(line).find()) {
                inVisit = true;
                // 괄호 안 → "라벨 : 값" → 방문 앞부분 순서로 첫 번째 장소를 쓴다
                List<String> candidates = new ArrayList<>();
                Matcher paren = VISIT_PAREN.matcher(line);
                if (paren.find()) {
                    candidates.add(paren.group(1));
                }
                String body = BULLET.matcher(line).replaceFirst("");
                Matcher lv = LABEL_VALUE.matcher(body);
                if (lv.find()) {
                    candidates.add(lv.group(1));
                    candidates.add(body.substring(0, body.indexOf(lv.group(1))).replaceAll("[:：\\s]+$", "")); // 라벨("주민센터")
                } else {
                    candidates.add(body);
                }
                for (String c : candidates) {
                    if (add(out, c)) {
                        break;
                    }
                }
                continue;
            }
            if (OTHER_METHOD.matcher(line).find()) {
                inVisit = false;
                continue;
            }
            if (inVisit && line.matches("^[\\-·*▶]\\s*.+") && !DOC_LINE.matcher(line).find()) {
                String body = BULLET.matcher(line).replaceFirst("");
                if (SKIP_LABEL.matcher(body).find()) {
                    continue;
                }
                Matcher lv = LABEL_VALUE.matcher(body);
                add(out, lv.find() ? lv.group(1) : body);
            }
        }
        return out.stream().limit(MAX_PLACES).toList();
    }

    /** 다듬어서 장소면 넣고 true */
    static boolean add(Set<String> out, String candidate) {
        String place = clean(candidate);
        if (place.length() >= 2 && place.length() <= 40 && PLACE_WORD.matcher(place).find()
                && !DOC_LINE.matcher(place).find()) {
            out.add(place);
            return true;
        }
        return false;
    }

    private static final Pattern PHONE = Pattern.compile("☎?\\s*\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]\\d{4}");
    private static final Pattern VISIT_HEAD = Pattern.compile("^(?:방문|내방)\\s*(?:신청|접수)?\\s*[-:：]?\\s*");

    static String clean(String candidate) {
        String p = NOTE_PAREN.matcher(nz(candidate)).replaceAll("").strip();
        p = PHONE.matcher(p).replaceAll("").strip();
        p = VISIT_HEAD.matcher(p).replaceFirst(""); // "방문 신청 - 중구보건소 1층"
        // "동주민센터 방문 신청, 접수 → 구청"처럼 방문 뒤는 동작 설명이라 앞부분만
        Matcher visit = VISIT.matcher(p);
        if (visit.find() && visit.start() > 0) {
            String before = p.substring(0, visit.start());
            if (PLACE_WORD.matcher(TRAILING.matcher(before.strip()).replaceAll("")).find() || before.contains("센터")) {
                p = before;
            }
        }
        String lead = LEADING_CLAUSE.matcher(p).replaceFirst("");
        if (!lead.equals(p) && PLACE_WORD.matcher(lead).find()) {
            p = lead;
        }
        String prev;
        do {
            prev = p;
            p = TRAILING.matcher(p.strip()).replaceAll("");
        } while (!p.equals(prev));
        return p.strip();
    }

    /** "경제과/033-560-2324||민원실/033-560-2000" → 문의처 목록 */
    static List<Contact> contacts(String contact) {
        List<Contact> out = new ArrayList<>();
        for (String part : nz(contact).split("\\|\\|")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            int slash = p.lastIndexOf('/');
            out.add(slash < 0 ? new Contact(p, "") : new Contact(p.substring(0, slash).strip(), p.substring(slash + 1).strip()));
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
