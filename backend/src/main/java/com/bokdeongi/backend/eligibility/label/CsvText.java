package com.bokdeongi.backend.eligibility.label;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 작은 CSV 읽기 (첫 줄 = 칸 이름, 큰따옴표 안의 쉼표·줄바꿈·"" 지원, BOM 무시). 라벨 파일 하나만 읽어서 라이브러리 없이 둔다.
 */
public final class CsvText {

    private CsvText() {
    }

    public static List<Map<String, String>> parse(String text) {
        List<List<String>> records = records(text.startsWith("﻿") ? text.substring(1) : text);
        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = records.get(0);
        for (int i = 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            if (r.size() == 1 && r.get(0).isEmpty()) {
                continue; // 빈 줄
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c).strip(), c < r.size() ? r.get(c) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> records(String s) {
        List<List<String>> out = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                out.add(record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            out.add(record);
        }
        return out;
    }
}
