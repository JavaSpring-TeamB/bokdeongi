package com.bokdeongi.backend.sync;

import com.bokdeongi.backend.benefit.service.AmountRuleExtractor;
import com.bokdeongi.backend.benefit.service.ApplyPeriodExtractor;
import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder;
import com.bokdeongi.backend.benefit.service.DocumentPrepBuilder.MasterDoc;
import com.bokdeongi.backend.benefit.service.ExclusionRuleExtractor;
import com.bokdeongi.backend.benefit.service.HashtagRuleTagger;
import com.bokdeongi.backend.eligibility.SupportConditionConverter;
import com.bokdeongi.backend.eligibility.store.JsonText;
import com.bokdeongi.backend.extraction.ExtractionValidator;
import com.bokdeongi.backend.extraction.PeriodStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * API 원문 한 건 → benefits 한 줄 (DATA-01). DB·Spring에 의존하지 않는다.
 *
 * <p>가공은 이미 있는 규칙 추출기를 그대로 부른다. LLM은 부르지 않는다.
 * <ul>
 *   <li>지원조건 코드 → 규칙·개인 추천 대상: {@link SupportConditionConverter} (DATA-02, D-48·49)</li>
 *   <li>지역: {@link OrgRegionResolver} (D-56)</li>
 *   <li>신청 기간: {@link ApplyPeriodExtractor} (DATA-04, D-51·66·84). 기간이 여러 개면 pickPeriod로 하나</li>
 *   <li>공고 상태: {@link PeriodStatus} (D-85). 기간이 지났으면 EXPIRED</li>
 *   <li>금액: {@link AmountRuleExtractor} (DATA-05)</li>
 *   <li>중복 지원 불가 문구: {@link ExclusionRuleExtractor} (DATA-06)</li>
 *   <li>구비서류: {@link DocumentPrepBuilder} (DATA-07, ASST-04와 같은 묶음 규칙)</li>
 *   <li>해시태그: {@link HashtagRuleTagger} (DATA-09). 사전(hashtags)에 없는 이름은 후보로 뺀다 (D-47)</li>
 * </ul>
 * 형식이 깨진 레코드(서비스ID·서비스명·수정일시가 없음)는 {@link InvalidRecordException}으로 격리한다.
 */
public final class BenefitMapper {

    /** 금액 근거 문구가 원문에서 그대로 확인되면 이 값, 아니면 LOW (규칙 추출이라 LLM 신뢰도와 다르다) */
    static final BigDecimal AMOUNT_CONFIDENCE = new BigDecimal("0.80");
    static final BigDecimal AMOUNT_CONFIDENCE_LOW = new BigDecimal("0.50");

    private static final Pattern ONLINE_WORDS = Pattern.compile("온라인|인터넷|누리집|홈페이지|정부24|복지로|모바일|앱");

    /** 형식이 깨진 레코드 */
    public static class InvalidRecordException extends RuntimeException {
        public InvalidRecordException(String message) {
            super(message);
        }
    }

    private final OrgRegionResolver regions;
    private final List<MasterDoc> documentTypes;
    private final Set<String> hashtagDictionary;
    private final LocalDate today;

    /**
     * @param hashtagDictionary hashtags 테이블의 이름. 비어 있으면(아직 시드 전) 거르지 않는다
     * @param today             한국 날짜
     */
    public BenefitMapper(OrgRegionResolver regions, List<MasterDoc> documentTypes, Set<String> hashtagDictionary,
            LocalDate today) {
        this.regions = regions;
        this.documentTypes = List.copyOf(documentTypes);
        this.hashtagDictionary = Set.copyOf(hashtagDictionary);
        this.today = today;
    }

    public BenefitRow map(ServiceRecord rec) {
        String serviceId = rec.serviceId();
        String title = rec.text("서비스명");
        LocalDateTime modifiedAt = rec.modifiedAtUtc();
        if (serviceId == null || title == null || modifiedAt == null) {
            throw new InvalidRecordException("필수 칸이 비었거나 형식이 다름 (서비스ID·서비스명·수정일시)");
        }
        List<String> warnings = new ArrayList<>();
        String userType = rec.listText("사용자구분");
        String supportType = rec.text("지원유형");
        String target = rec.text("지원대상");
        String criteria = rec.text("선정기준");
        String content = rec.text("지원내용");
        String periodType = rec.text("신청기한유형");
        String periodText = rec.text("신청기한");
        String requiredDocs = rec.text("구비서류");
        String officialDocs = rec.text("공무원확인구비서류");
        Integer year = rec.modifiedYear();

        // 지원조건 코드
        Map<String, String> ja = rec.jaCodes();
        Map<String, String> forConverter = new LinkedHashMap<>();
        ja.forEach((k, v) -> forConverter.put(k, v == null ? "" : v));
        SupportConditionConverter.Result converted = SupportConditionConverter.convert(forConverter, userType);
        warnings.addAll(converted.warnings());
        List<String> yCodes = ja.entrySet().stream().filter(e -> "Y".equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey).toList();
        Integer ageMin = integer(ja.get("JA0110"));
        Integer ageMax = integer(ja.get("JA0111"));
        StringBuilder raw = new StringBuilder();
        JsonText.writeObject(raw, ja);

        // 지역
        OrgRegionResolver.Resolved region = regions.resolve(rec);
        if (!region.found()) {
            warnings.add("지역 미확인: " + rec.text("소관기관명"));
        }

        // 신청 기간
        ApplyPeriodExtractor.Result period = ApplyPeriodExtractor.extract(periodType, periodText, year);
        LocalDate applyStart = null;
        LocalDate applyEnd = null;
        String precision = null;
        if (period.status() == ApplyPeriodExtractor.Status.PARSED) {
            precision = period.precision() == null ? null : period.precision().name();
            ApplyPeriodExtractor.Period picked = ApplyPeriodExtractor.pickPeriod(period, today).orElse(null);
            if (picked != null) {
                applyStart = startDate(picked.start());
                applyEnd = endDate(picked.end());
            }
        }
        boolean expired = PeriodStatus.classify(periodType == null ? "" : periodType, periodText, target, year, today)
                .label().expired();

        // 금액
        AmountRuleExtractor.Result amount = AmountRuleExtractor.extract(content, supportType, title);
        Integer maxAmount = intAmount(amount.maxAmount(), warnings, "max_amount");
        BigDecimal confidence = null;
        if (maxAmount != null) {
            confidence = ExtractionValidator.quoteFound(amount.quote(), sources(content))
                    ? AMOUNT_CONFIDENCE : AMOUNT_CONFIDENCE_LOW;
        }

        // 중복 지원 불가
        String exclusion = ExclusionRuleExtractor.extract(target, criteria, content);
        String exclusionsJson = exclusion.isEmpty() ? "[]" : objects(List.of(one("sourceQuote", exclusion)));

        // 구비서류: 내가 낼 서류(SELF) + 공무원이 확인하는 서류(OFFICIAL, 낼 서류 수에는 안 셈)
        DocumentPrepBuilder.Prep self = DocumentPrepBuilder.build(requiredDocs, documentTypes);
        DocumentPrepBuilder.Prep official = DocumentPrepBuilder.build(officialDocs, documentTypes);
        List<Map<String, Object>> docs = new ArrayList<>();
        addDocs(docs, self, "SELF");
        addDocs(docs, official, "OFFICIAL");

        String onlineUrl = rec.text("온라인신청사이트URL");
        String method = rec.text("신청방법");
        boolean online = onlineUrl != null || (method != null && ONLINE_WORDS.matcher(method).find());

        // 해시태그
        List<String> tags = HashtagRuleTagger.tag(new HashtagRuleTagger.Input(title, rec.listText("서비스분야"),
                supportType, userType, target, content, new LinkedHashSet<>(yCodes), ageMin, ageMax));
        List<String> kept = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        for (String tag : tags) {
            (hashtagDictionary.isEmpty() || hashtagDictionary.contains(tag) ? kept : candidates).add(tag);
        }

        return new BenefitRow(
                cut(serviceId, 20), cut(title, 200), rec.text("서비스목적요약", "서비스목적"), cut(supportType, 30),
                target, criteria, content, method, periodText, requiredDocs, officialDocs,
                rec.text("본인확인필요구비서류"),
                cut(rec.text("소관기관명"), 255), cut(rec.listText("부서명"), 255),
                cut(rec.text("접수기관명", "접수기관"), 255), cut(rec.text("문의처", "전화문의"), 255),
                cut(onlineUrl, 500), cut(rec.listText("상세조회URL"), 500),
                cut(rec.listText("서비스분야"), 30), cut(userType, 30), integer(rec.listText("조회수")),
                modifiedAt, expired ? "EXPIRED" : "ACTIVE",
                ageMin, ageMax, JsonText.writeStrings(yCodes), raw.toString(), converted.targetScope().name(),
                region.code(),
                applyStart, applyEnd, period.status().name(), precision, emptyToNull(period.quote()),
                period.recurringYearly(),
                maxAmount, amount.period() == null ? null : amount.period().name(),
                intAmount(amount.annualAmount(), warnings, "annual_amount"), confidence,
                maxAmount == null ? null : emptyToNull(amount.quote()),
                exclusionsJson, objects(docs), online, self.total(),
                JsonText.writeStrings(kept), JsonText.writeStrings(candidates),
                converted.rules(), warnings);
    }

    private static void addDocs(List<Map<String, Object>> out, DocumentPrepBuilder.Prep prep, String submitType) {
        for (DocumentPrepBuilder.IssuerGroup g : prep.groups()) {
            for (DocumentPrepBuilder.PrepItem item : g.items()) {
                out.add(doc(item.doc().id(), item.sourceName(), submitType, item.doc().defaultValidityDays()));
            }
        }
        for (String name : prep.forms()) {
            out.add(doc(null, name, submitType, null));
        }
        for (String name : prep.unknown()) {
            out.add(doc(null, name, submitType, null));
        }
    }

    private static Map<String, Object> doc(Long typeId, String rawName, String submitType, Integer validityDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documentTypeId", typeId);
        m.put("rawName", rawName);
        m.put("submitType", submitType);
        m.put("validityDays", validityDays);
        m.put("conditionText", null);
        return m;
    }

    private static Map<String, Object> one(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    public static String objects(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            JsonText.writeObject(sb, items.get(i));
        }
        return sb.append(']').toString();
    }

    private static List<String> sources(String text) {
        return text == null ? List.of() : List.of(text);
    }

    static LocalDate startDate(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() == 7 ? YearMonth.parse(s).atDay(1) : LocalDate.parse(s);
    }

    static LocalDate endDate(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() == 7 ? YearMonth.parse(s).atEndOfMonth() : LocalDate.parse(s);
    }

    /** int 칸(최대 약 21억)을 넘는 금액은 비우고 경고로 남긴다 */
    private static Integer intAmount(Long value, List<String> warnings, String column) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE || value < 0) {
            warnings.add(column + " 범위 초과: " + value);
            return null;
        }
        return value.intValue();
    }

    static Integer integer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
