package com.bokdeongi.backend.benefit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 구비서류 칸에서 신청자가 직접 낼 서류 이름을 규칙으로 뽑는다. LLM을 부르지 않는다. (DATA-07)
 *
 * 1. "구비서류 없음", "해당없음", "제출 불필요"면 빈 목록
 * 2. 줄마다 나누고 ※·*로 시작하는 안내, 대리인·담당자 확인·선택사항 줄은 뺀다
 * 3. 괄호 밖의 쉼표·빗금, " 및 "으로 다시 나눈다
 * 4. 글머리(○ ㅇ - ① 1.), 머리말("민원인:", "(필수서류)"), 괄호 설명, "1부", "등"을 지운다
 * 5. 서·증·원·본·카드·사본·영수증처럼 서류 이름으로 끝나는 것만 남긴다
 * 공무원 확인 서류(행정정보 공동이용) 칸은 넣지 않는다 (입력으로 받지 않음).
 */
public final class DocumentRuleExtractor {

    private static final Pattern NONE = Pattern.compile("해당\\s*없음|구비서류\\s*없음|제출\\s*불필요|별도\\s*(제출\\s*)?없음|^\\s*없음\\s*$|서류\\s*없음");
    private static final Pattern SKIP_LINE = Pattern.compile("^\\s*[※*]|대리인|담당자\\s*:|\\(선택|선택사항|발급일|원칙|구비|다운|서식|외국인");
    private static final Pattern HEADER_LINE = Pattern.compile("^\\s*[○ㅇ-]?\\s*\\(?[가-힣\\s]{0,10}(공통서류|필수서류|제출서류|지참서류)\\)?\\s*:?\\s*$");
    private static final Pattern DOC_WORD = Pattern.compile("(서|증|원|본|카드|쿠폰|자료|통장|사본|기록지|내역서|영수증|서류)$");
    private static final Pattern AND = Pattern.compile("\\s+및\\s+");
    private static final Pattern PAREN = Pattern.compile("\\([^)]*\\)");

    private DocumentRuleExtractor() {
    }

    public static List<String> extract(String requiredDocsText) {
        List<String> items = new ArrayList<>();
        if (requiredDocsText == null || requiredDocsText.isBlank() || NONE.matcher(requiredDocsText).find()) {
            return items;
        }
        for (String line : requiredDocsText.split("\n")) {
            if (line.isBlank() || SKIP_LINE.matcher(line).find() || HEADER_LINE.matcher(line).find()) {
                continue;
            }
            for (String part : splitTopLevel(line)) {
                for (String piece : AND.split(PAREN.matcher(part).replaceAll(""))) {
                    String name = clean(piece);
                    if (name.length() >= 2 && name.length() <= 30 && DOC_WORD.matcher(name).find() && !items.contains(name)) {
                        items.add(name);
                    }
                }
            }
        }
        return items;
    }

    /** 괄호·꺾쇠 안이 아닌 쉼표와 빗금에서만 나눈다 ("가족관계증명서(원가구, 본인 포함)"는 하나) */
    static List<String> splitTopLevel(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char ch : line.toCharArray()) {
            if (ch == '(' || ch == '[' || ch == '<') {
                depth++;
            }
            if (ch == ')' || ch == ']' || ch == '>') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && (ch == ',' || ch == '/')) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static String clean(String item) {
        String t = item.strip();
        t = t.replaceFirst("^[\\-○ㅇ·•▶◦①②③④⑤⑥⑦⑧⑨⑩\\d.)\\s]+", "");
        t = t.replaceFirst("^\\([^)]*\\)\\s*", "");
        t = t.replaceFirst("^[^:：]{1,15}[:：]\\s*", "");
        t = PAREN.matcher(t).replaceAll("");
        t = t.replace("*", "");
        t = t.replaceFirst("\\s*\\d+\\s*부$", "");
        if (t.contains("의 ") && t.length() > 8) {
            t = t.replaceFirst("^.*의\\s+", "");
        }
        t = t.replaceFirst("\\s*등$", "");
        if (t.length() > 12) {
            t = t.replaceFirst("^.*(?:다는|하는|되는|된)\\s+", "");
        }
        return t.replaceAll("\\s+", " ").replaceAll("^[ .,]+|[ .,]+$", "");
    }
}
