package com.bokdeongi.backend.benefit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ④ 신청 안내 (ASST-05). LLM을 부르지 않는다.
 *
 * - 신청 방법: 온라인(신청 주소·누리집 이름이 있으면) / 방문 / 둘 다 / 그 밖(우편·전화·자동 지급 등)
 * - 신청 주소: 온라인 신청 주소 칸 → 신청방법 원문의 주소 → 원문에 적힌 누리집(복지로·정부24·홈택스·고용24) 순서
 * - 단계: 신청방법 원문을 줄·화살표(→)·번호(①)로 나눠 순서대로 보여 준다. 원문에 없는 말은 만들지 않는다
 *   "○ 방문 신청" 같은 머리줄은 단계가 아니라 그 아래 줄들의 신청 방법(method)이 된다
 * - ※, * 로 시작하는 줄은 단계가 아니라 참고(notes)로 뺀다
 */
public final class ApplyGuideBuilder {

    public static final String ONLINE = "ONLINE";
    public static final String VISIT = "VISIT";
    public static final String ONLINE_OR_VISIT = "ONLINE_OR_VISIT";
    public static final String OTHER = "OTHER";

    public static final String PHONE = "PHONE";
    public static final String MAIL = "MAIL";

    /** 공고 원문 칸 (benefits 테이블의 같은 이름 칸) */
    public record Input(String applyMethodText, String onlineApplyUrl, Boolean onlineApply) {
    }

    /**
     * 신청 단계 하나.
     *
     * @param method 이 단계의 신청 방법 (ONLINE / VISIT / PHONE / MAIL). 원문에서 알 수 없으면 null
     * @param text   원문 그대로의 단계 문장 (머리 기호만 지움)
     */
    public record Step(String method, String text) {
    }

    /**
     * @param method   ONLINE / VISIT / ONLINE_OR_VISIT / OTHER
     * @param applyUrl 공식 신청 페이지. 모르면 null
     * @param siteName 신청 누리집 이름 ("복지로", "정부24" ...). 모르면 null
     * @param steps    신청 단계 (원문 순서, 최대 MAX_STEPS개)
     * @param notes    참고 사항 (※, * 줄)
     */
    public record Guide(String method, String applyUrl, String siteName, List<Step> steps, List<String> notes) {
    }

    static final int MAX_STEPS = 10;
    private static final int MAX_TEXT = 200;

    /** 원문에 이름만 적힌 누리집 → 첫 화면 주소 */
    private static final Map<String, String> SITE_URL = Map.of(
            "복지로", "https://www.bokjiro.go.kr",
            "정부24", "https://www.gov.kr",
            "홈택스", "https://www.hometax.go.kr",
            "고용24", "https://www.work24.go.kr");
    /** 주소 안의 도메인 → 누리집 이름 */
    private static final Map<String, String> HOST_NAME = Map.of(
            "bokjiro.go.kr", "복지로",
            "gov.kr", "정부24",
            "hometax.go.kr", "홈택스",
            "work24.go.kr", "고용24");

    /** "https://...", "www....", 그리고 "mindup.kicece.or.kr"처럼 앞을 뺀 주소 (이메일 주소의 도메인은 빼고) */
    private static final Pattern URL = Pattern.compile(
            "(?:https?://|www\\.)[^\\s()（）\\]>,'\"가-힣]+"
            + "|(?<![@\\w.])[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*\\.(?:kr|com|net|org)(?:/[^\\s()（）\\]>,'\"가-힣]*)?");
    private static final Pattern ONLINE_WORD = Pattern.compile(
            "온라인|인터넷|홈페이지|누리집|복지로|정부24|고용24|홈택스|모바일|어플|(?<![가-힣])앱|https?://|www\\.");
    /** "홈페이지 직접 신청"이 있어서 "직접"은 방문으로 보지 않는다 */
    private static final Pattern VISIT_WORD = Pattern.compile("방문|내방|창구");

    /** 줄 앞 기호: "○", "-", "ㅇ", "☞", "1.", "①" */
    private static final Pattern BULLET = Pattern.compile("^(?:[\\s○●◎■□▶▷·\\-*ㅇ☞>]+|\\d+[.)]\\s*|[①-⑳]\\s*)+");
    /** 번호로 시작하는 줄: "① ...", "2. ..." */
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(?:[①-⑳]|\\d+[.)])");
    private static final Pattern NOTE = Pattern.compile("^\\s*[※*]");
    /** 신청 방법 머리줄: "방문 신청", "온라인 신청 :", "기타" */
    private static final Pattern HEADER = Pattern.compile(
            "^\\(?(온라인|인터넷|방문|모바일|우편|팩스|이메일|전화|유선|기타)\\s*(?:신청|접수)?\\)?\\s*(?:[:：]\\s*(.*))?$");
    /** 단계를 나누는 곳: 줄바꿈, 앞뒤 띄운 빗금, 화살표, 번호 */
    private static final Pattern SPLIT = Pattern.compile("\\r?\\n|\\s/\\s(?!\\d)|\\s*(?:->|→|⇒)\\s*|(?=[①-⑳])");

    private ApplyGuideBuilder() {
    }

    public static Guide build(Input in) {
        String text = nz(in.applyMethodText());
        List<String> notes = new ArrayList<>();
        List<Step> steps = steps(text, notes);

        String applyUrl = url(in.onlineApplyUrl());
        if (applyUrl == null) {
            applyUrl = url(text);
        }
        String siteName = applyUrl == null ? null : siteOf(applyUrl);
        if (applyUrl == null) {
            for (Map.Entry<String, String> e : SITE_URL.entrySet()) {
                if (text.contains(e.getKey())) {
                    siteName = e.getKey();
                    applyUrl = e.getValue();
                    break;
                }
            }
        }

        boolean online = applyUrl != null || Boolean.TRUE.equals(in.onlineApply()) || ONLINE_WORD.matcher(text).find();
        boolean visit = VISIT_WORD.matcher(text).find();
        String method = online && visit ? ONLINE_OR_VISIT : online ? ONLINE : visit ? VISIT : OTHER;
        return new Guide(method, applyUrl, siteName, steps, notes);
    }

    /** 신청방법 원문 → 단계. 참고 줄은 notes에 넣는다 */
    static List<Step> steps(String text, List<String> notes) {
        List<Step> out = new ArrayList<>();
        boolean pendingNumbered = false;
        Step pending = null; // 머리줄만 있는 줄. 아래에 딸린 줄이 오면 버리고, 다음 머리줄이나 끝이 오면 단계로 넣는다
        String current = null;
        for (String raw : SPLIT.split(nz(text))) {
            if (raw.isBlank()) {
                continue;
            }
            if (NOTE.matcher(raw).find()) {
                String note = trim(raw.replaceFirst("^\\s*[※*]+\\s*", ""));
                if (!note.isEmpty() && !notes.contains(note)) {
                    notes.add(note);
                }
                continue;
            }
            String line = trim(BULLET.matcher(raw.strip()).replaceFirst(""));
            if (line.length() < 2) {
                continue;
            }
            Matcher h = HEADER.matcher(line);
            if (h.matches()) {
                add(out, pending); // "② 이메일 접수" 다음에 또 머리줄이 오면 앞의 것도 단계
                current = methodOf(h.group(1));
                String rest = trim(h.group(2));
                if (rest.length() < 2) {
                    pending = new Step(current, line); // "○ 방문 신청"
                    pendingNumbered = NUMBERED.matcher(raw).find();
                    continue;
                }
                pending = null;
                line = rest;
            } else {
                if (pending != null && NUMBERED.matcher(raw).find() && pendingNumbered) {
                    add(out, pending); // "② 이메일 접수" 다음 "③ 방문 접수(...)": 번호가 이어지면 앞의 것도 단계
                }
                pending = null; // 그 밖에는 머리줄 아래 딸린 줄이라 머리줄은 단계가 아니다
            }
            add(out, new Step(current != null ? current : guess(line), line));
            if (out.size() >= MAX_STEPS) {
                return out.subList(0, MAX_STEPS);
            }
        }
        add(out, pending);
        return out.size() > MAX_STEPS ? out.subList(0, MAX_STEPS) : out;
    }

    private static void add(List<Step> out, Step step) {
        if (step != null && !out.contains(step)) {
            out.add(step);
        }
    }

    private static String methodOf(String header) {
        return switch (header) {
            case "온라인", "인터넷", "모바일", "이메일" -> ONLINE;
            case "방문" -> VISIT;
            case "전화", "유선" -> PHONE;
            case "우편", "팩스" -> MAIL;
            default -> null; // 기타
        };
    }

    /** 머리줄 없이 쓴 줄은 낱말로 짐작한다. 둘 다 있거나 없으면 null */
    private static String guess(String line) {
        boolean online = ONLINE_WORD.matcher(line).find();
        boolean visit = VISIT_WORD.matcher(line).find();
        return online == visit ? null : online ? ONLINE : VISIT;
    }

    /** 글에서 첫 주소를 꺼낸다. "www.bokjiro.go.kr." → "https://www.bokjiro.go.kr". 주소가 없으면 null */
    static String url(String s) {
        Matcher m = URL.matcher(nz(s));
        if (!m.find()) {
            return null;
        }
        String u = m.group().replaceAll("[.。]+$", "");
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u; // "www.bokjiro.go.kr", "mindup.kicece.or.kr"
        }
        return u;
    }

    static String siteOf(String url) {
        String host = url.replaceFirst("^https?://", "").replaceFirst("[/?#:].*$", "");
        for (Map.Entry<String, String> e : HOST_NAME.entrySet()) {
            if (host.equals(e.getKey()) || host.endsWith("." + e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String trim(String s) {
        String t = nz(s).replaceAll("\\s+", " ").strip();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT - 1) + "…" : t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
