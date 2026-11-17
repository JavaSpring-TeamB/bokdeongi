package com.bokdeongi.backend.eligibility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 테스트용 CSV 읽기 (RFC 4180: 큰따옴표 칸, 칸 안의 쉼표·줄바꿈·"" 이스케이프). 첫 줄은 머리글. */
final class MiniCsv {

    private MiniCsv() {
    }

    static List<Map<String, String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() == 1 && r.get(0).isBlank()) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                m.put(header.get(j), j < r.size() ? r.get(j).strip() : "");
            }
            result.add(m);
        }
        return result;
    }
}
