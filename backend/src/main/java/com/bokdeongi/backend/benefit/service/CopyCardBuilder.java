package com.bokdeongi.backend.benefit.service;

import com.bokdeongi.backend.profile.dto.response.ProfileResponse;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ④ 입력값 복사 카드 (ASST-05). 신청 사이트 신청서에 그대로 붙여 넣을 내 프로필 값.
 *
 * - 프로필에 값이 있는 항목만 만든다
 * - 주민등록번호·상세 주소는 프로필에 없고, 혹시 값에 주민등록번호 모양이 섞여 있으면 그 카드는 만들지 않는다 (AGENTS.md 4번)
 * - 소득·수급 여부·민감정보는 신청서마다 묻는 방식이 달라 카드로 만들지 않는다
 */
public final class CopyCardBuilder {

    /**
     * @param key   항목 키 (프론트 아이콘·순서용)
     * @param label 화면에 보여 줄 이름
     * @param value 복사할 값
     */
    public record CopyCard(String key, String label, String value) {
    }

    private static final Pattern RRN = Pattern.compile("\\d{6}\\s*-?\\s*[1-8]\\d{6}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> GENDER = Map.of("M", "남성", "F", "여성");
    private static final Map<String, String> HOUSING = Map.of(
            "OWN", "자가", "JEONSE", "전세", "MONTHLY", "월세", "ETC", "기타");
    private static final Map<String, String> MARITAL = Map.of("SINGLE", "미혼", "MARRIED", "기혼", "ETC", "기타");

    private CopyCardBuilder() {
    }

    public static List<CopyCard> build(ProfileResponse p) {
        List<CopyCard> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        add(out, "name", "이름", p.name());
        add(out, "birthDate", "생년월일", p.birthDate() == null ? null : p.birthDate().format(DATE));
        add(out, "gender", "성별", GENDER.get(nz(p.gender())));
        add(out, "phone", "휴대전화", p.phoneNumber());
        add(out, "email", "이메일", p.email());
        add(out, "region", "거주지(시·군·구)", (nz(p.sidoName()) + " " + nz(p.sigunguName())).strip());
        add(out, "householdSize", "가구원 수", p.householdSize() == null ? null : p.householdSize() + "명");
        add(out, "housingType", "주거 형태", HOUSING.get(nz(p.housingType())));
        add(out, "maritalStatus", "혼인 상태", MARITAL.get(nz(p.maritalStatus())));
        add(out, "childrenCount", "자녀 수", p.childrenCount() == null ? null : p.childrenCount() + "명");
        return out;
    }

    private static void add(List<CopyCard> out, String key, String label, String value) {
        if (value == null || value.isBlank() || RRN.matcher(value).find()) {
            return;
        }
        out.add(new CopyCard(key, label, value.strip()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
