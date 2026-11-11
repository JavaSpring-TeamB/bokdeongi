"""수집한 원본으로 실측 보고서를 만든다.

사용법 (레포 루트에서):
    python data-lab/api_survey/analyze.py

결과: data-lab/reports/api_survey.md (커밋 대상, 공개 공고 데이터만 포함)
필드 이름은 하드코딩을 최소화했다. 예상한 필드가 없으면 보고서에 '필드 없음'과 실제 필드 목록을 남긴다.
"""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ENDPOINTS, RAW_DIR, REPORT_PATH  # noqa: E402

ID_KEY = "서비스ID"
PERIOD_KEY = "신청기한"
DOC_KEYS = ("구비서류", "공무원확인구비서류", "본인확인필요구비서류")
MODIFIED_KEY = "수정일시"
CATEGORY_KEYS = ("지원유형", "서비스분야", "사용자구분", "소관기관유형")
JA_PATTERN = re.compile(r"^JA\d{4}$")

PERIOD_TYPE_KEY = "신청기한유형"
TARGET_KEY = "사용자구분"
# 다중 값 구분자. API 원문은 "||"를 쓴다 (보고서 표에서는 "/"로 보이도록 바꿔 출력)
MULTI_SEPARATOR = re.compile(r"\|\||//")
PERSONAL_TARGETS = ("개인", "가구")

# 지원조건 코드 그룹: 코드 앞 4자리(JA01, JA02 ...)로 묶는다. JA0110·JA0111은 나이(숫자)라 따로 본다
AGE_CODES = ("JA0110", "JA0111")
NO_AGE_LIMIT = {("0", "120"), ("", ""), ("0", ""), ("", "120")}

# 신청기한 분류: 위에서부터 먼저 맞는 것으로 정한다
PERIOD_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("날짜(연도 포함)", re.compile(r"(19|20)\d{2}\s*[.\-/년]\s*\d{1,2}\s*[.\-/월]\s*\d{1,2}")),
    ("날짜(연도 없음)", re.compile(r"(?<!\d)\d{1,2}\s*[.\-/월]\s*\d{1,2}\s*일?")),
    ("상대 기간", re.compile(r"(로부터|부터|후)\s*\d+\s*(일|주|개월|년)|\d+\s*(일|주|개월)\s*이내")),
    ("신청 불필요", re.compile(r"신청\s*불필요|신청\s*절차(가)?\s*없|신청하지\s*않")),
    ("기관별 상이", re.compile(r"상이|다를\s*수|다름")),
    ("매년 반복(월 단위)", re.compile(r"매년|해마다|연초|연말|(?<!\d)\d{1,2}\s*월")),
    ("상시", re.compile(r"상시|수시|연중")),
    ("예산 소진·선착순", re.compile(r"예산\s*소진|소진\s*시|선착순")),
    ("공고·문의 참조", re.compile(r"공고|별도|홈페이지|문의|참조|확인")),
]

# 값이 있어도 내용이 없는 것으로 보는 문구 (공무원확인구비서류 등에 '해당없음'이 대부분)
EMPTY_TEXTS = {"", "null", "None", "-", "해당없음", "해당 없음", "없음"}


def is_filled(value: object) -> bool:
    if value is None:
        return False
    return str(value).strip() not in EMPTY_TEXTS


def classify_period(text: object) -> str:
    if not is_filled(text):
        return "비어 있음"
    for label, pattern in PERIOD_RULES:
        if pattern.search(str(text)):
            return label
    return "기타"


def load_records(endpoint: str, raw_dir: Path = RAW_DIR) -> list[dict]:
    folder = raw_dir / endpoint
    records: list[dict] = []
    for path in sorted(folder.glob("page_*.json")):
        records.extend(json.loads(path.read_text(encoding="utf-8")).get("data", []))
    return records


def total_count(endpoint: str, raw_dir: Path = RAW_DIR) -> int | None:
    first = raw_dir / endpoint / "page_0001.json"
    if not first.exists():
        return None
    meta = json.loads(first.read_text(encoding="utf-8"))
    return int(meta.get("totalCount") or meta.get("matchCount") or 0)


def fill_rates(records: list[dict]) -> list[tuple[str, int, float]]:
    keys: list[str] = []
    for record in records:
        for key in record:
            if key not in keys:
                keys.append(key)
    rows = []
    for key in keys:
        filled = sum(1 for record in records if is_filled(record.get(key)))
        rows.append((key, filled, filled / len(records) * 100 if records else 0.0))
    return rows


def ja_stats(records: list[dict]) -> tuple[list[tuple[str, Counter]], int]:
    """코드별 값 분포와, Y가 하나도 없는 서비스 수(조건 미표기)를 돌려준다."""
    codes = sorted({key for record in records for key in record if JA_PATTERN.match(key)})
    stats = [(code, Counter(str(record.get(code)).strip() if is_filled(record.get(code)) else "(빈 값)" for record in records)) for code in codes]
    no_condition = sum(
        1 for record in records
        if not any(str(record.get(code)).strip().upper() == "Y" for code in codes)
    )
    return stats, no_condition


def split_multi(value: object) -> list[str]:
    """'현금//현물' 같은 다중 값을 나눈다."""
    if not is_filled(value):
        return []
    return [part.strip() for part in MULTI_SEPARATOR.split(str(value)) if part.strip()]


def is_personal(record: dict) -> bool:
    """사용자구분에 개인 또는 가구가 들어 있으면 개인 회원 추천 대상이다."""
    return any(target in split_multi(record.get(TARGET_KEY)) for target in PERSONAL_TARGETS)


def ja_groups(records: list[dict]) -> dict[str, list[str]]:
    codes = sorted({key for record in records for key in record if JA_PATTERN.match(key) and key not in AGE_CODES})
    groups: dict[str, list[str]] = {}
    for code in codes:
        groups.setdefault(code[:4], []).append(code)
    return groups


def is_y(value: object) -> bool:
    return str(value).strip().upper() == "Y"


def group_pattern(record: dict, codes: list[str]) -> str:
    """그룹 안에서 Y가 하나도 없음 / 전부 Y / 일부만 Y."""
    y_count = sum(1 for code in codes if is_y(record.get(code)))
    if y_count == 0:
        return "Y 없음"
    if y_count == len(codes):
        return "전부 Y"
    return "일부 Y"


def age_pattern(record: dict) -> str:
    low = str(record.get("JA0110") or "").strip()
    high = str(record.get("JA0111") or "").strip()
    if (low, high) in NO_AGE_LIMIT:
        return "제한 없음(0~120 또는 빈 값)"
    return "나이 제한 있음"


def parse_datetime(text: object) -> datetime | None:
    if not is_filled(text):
        return None
    digits = re.sub(r"\D", "", str(text))
    for length, fmt in ((14, "%Y%m%d%H%M%S"), (12, "%Y%m%d%H%M"), (8, "%Y%m%d")):
        if len(digits) >= length:
            try:
                return datetime.strptime(digits[:length], fmt)
            except ValueError:
                continue
    return None


def shorten(text: object, limit: int = 90) -> str:
    one_line = re.sub(r"\s+", " ", str(text)).strip().replace("|", "/")
    return one_line if len(one_line) <= limit else one_line[:limit] + "…"


def section_overview(lines: list[str], data: dict[str, list[dict]], raw_dir: Path) -> None:
    lines += ["## 1. 수집 건수", "", "| API | totalCount | 받은 건수 | 고유 서비스ID |", "| --- | --- | --- | --- |"]
    for endpoint, records in data.items():
        ids = {record.get(ID_KEY) for record in records if record.get(ID_KEY)}
        lines.append(f"| {endpoint} | {total_count(endpoint, raw_dir)} | {len(records)} | {len(ids)} |")
    id_sets = {endpoint: {r.get(ID_KEY) for r in records if r.get(ID_KEY)} for endpoint, records in data.items()}
    if all(id_sets.values()) and len(id_sets) == 3:
        a, b, c = (id_sets[e] for e in ENDPOINTS)
        lines += [
            "",
            f"- 3종 모두 있는 서비스: {len(a & b & c)}건",
            f"- 목록에만 있고 상세 없음: {len(a - b)}건 / 지원조건 없음: {len(a - c)}건",
            f"- 상세·지원조건에만 있고 목록에 없음: {len((b | c) - a)}건",
        ]
    lines.append("")


def section_fill(lines: list[str], data: dict[str, list[dict]]) -> None:
    lines += ["## 2. 필드별 채움률", ""]
    for endpoint, records in data.items():
        if endpoint == "supportConditions":
            continue  # JA 코드는 4절에서 따로 본다
        lines += [f"### {endpoint}", "", "| 필드 | 채워진 건수 | 채움률 |", "| --- | --- | --- |"]
        for key, filled, pct in fill_rates(records):
            lines.append(f"| {key} | {filled} | {pct:.1f}% |")
        lines.append("")


def section_period(lines: list[str], records: list[dict]) -> None:
    lines += ["## 3. 신청기한 형식 분포 (DATA-04, D-11)", ""]
    if not records or PERIOD_KEY not in records[0]:
        lines += [f"'{PERIOD_KEY}' 필드 없음. 실제 필드: {', '.join(records[0].keys()) if records else '-'}", ""]
        return
    groups: dict[str, list[str]] = {}
    for record in records:
        groups.setdefault(classify_period(record.get(PERIOD_KEY)), []).append(record.get(PERIOD_KEY))
    lines += ["| 분류 | 건수 | 비율 | 예시 |", "| --- | --- | --- | --- |"]
    for label, values in sorted(groups.items(), key=lambda item: -len(item[1])):
        examples = " / ".join(shorten(v, 40) for v in list(dict.fromkeys(values))[:3] if is_filled(v))
        lines.append(f"| {label} | {len(values)} | {len(values) / len(records) * 100:.1f}% | {examples} |")
    top = Counter(shorten(r.get(PERIOD_KEY), 60) for r in records if is_filled(r.get(PERIOD_KEY))).most_common(15)
    lines += ["", "자주 나오는 원문 상위 15개", "", "| 원문 | 건수 |", "| --- | --- |"]
    lines += [f"| {text} | {count} |" for text, count in top]
    lines.append("")


def section_ja(lines: list[str], records: list[dict]) -> None:
    lines += ["## 4. 지원조건 JA 코드 (DATA-02)", ""]
    stats, no_condition = ja_stats(records)
    if not stats:
        lines += ["JA 코드 필드 없음. 실제 필드: " + (", ".join(records[0].keys()) if records else "-"), ""]
        return
    lines += [
        f"- 코드 수: {len(stats)}개",
        f"- Y가 하나도 없는 서비스(조건 미표기 → DATA-03 대상): {no_condition}건 ({no_condition / len(records) * 100:.1f}%)",
        "",
        "| 코드 | Y | 빈 값 | 그 외 값(상위 3) |",
        "| --- | --- | --- | --- |",
    ]
    for code, counter in stats:
        others = [(value, count) for value, count in counter.most_common() if value.upper() != "Y" and value != "(빈 값)"]
        other_text = ", ".join(f"{shorten(value, 15)}({count})" for value, count in others[:3])
        y_count = sum(count for value, count in counter.items() if value.upper() == "Y")
        lines.append(f"| {code} | {y_count} | {counter.get('(빈 값)', 0)} | {other_text} |")
    lines += ["", "코드 이름(JA0101 = 남성 등)은 Swagger 명세와 대조해 이 표 옆에 적는다.", ""]


def section_ja_groups(lines: list[str], conditions: list[dict], targets: dict[str, dict]) -> None:
    """그룹별로 '조건 없음(전부 Y·Y 없음)'과 '실제 조건(일부 Y)'이 얼마나 되는지 본다 (DATA-02 해석 규칙)."""
    lines += ["## 4-1. 지원조건 그룹별 패턴 (DATA-02 해석 규칙)", ""]
    if not conditions:
        lines += ["지원조건 데이터 없음", ""]
        return
    lines += ["| 그룹 | 코드 | Y 없음 | 전부 Y | 일부 Y | 'Y 없음' 중 개인·가구 대상 |", "| --- | --- | --- | --- | --- | --- |"]
    for group, codes in ja_groups(conditions).items():
        counter = Counter()
        none_personal = 0
        for record in conditions:
            pattern = group_pattern(record, codes)
            counter[pattern] += 1
            if pattern == "Y 없음" and is_personal(targets.get(record.get(ID_KEY), {})):
                none_personal += 1
        lines.append(
            f"| {group} | {', '.join(codes)} | {counter['Y 없음']} | {counter['전부 Y']} | {counter['일부 Y']} | {none_personal} |"
        )
    ages = Counter(age_pattern(record) for record in conditions)
    lines += ["", "나이(JA0110 최소 / JA0111 최대)", "", "| 구분 | 건수 |", "| --- | --- |"]
    lines += [f"| {label} | {count} |" for label, count in ages.most_common()]
    pairs = Counter(
        f"{record.get('JA0110')}~{record.get('JA0111')}" for record in conditions if age_pattern(record) == "나이 제한 있음"
    )
    lines += ["", "자주 나오는 나이 범위 상위 10개: " + ", ".join(f"{pair}({count})" for pair, count in pairs.most_common(10)), ""]


def section_targets(lines: list[str], records: list[dict]) -> None:
    lines += ["## 6-1. 개인 회원 추천 대상 (사용자구분)", ""]
    if not records:
        lines += ["데이터 없음", ""]
        return
    personal = sum(1 for record in records if is_personal(record))
    biz_only = [r for r in records if not is_personal(r)]
    biz_counter = Counter(str(r.get(TARGET_KEY)) for r in biz_only)
    lines += [
        f"- 개인 또는 가구가 포함된 서비스: {personal}건 ({personal / len(records) * 100:.1f}%)",
        f"- 개인·가구 없이 소상공인·법인/시설/단체만 대상: {len(biz_only)}건",
        "",
        "| 사용자구분 (개인·가구 없음) | 건수 |",
        "| --- | --- |",
    ]
    lines += [f"| {shorten(value, 50)} | {count} |" for value, count in biz_counter.most_common()]
    lines.append("")


def section_split_values(lines: list[str], records: list[dict]) -> None:
    lines += ["## 6-2. 지원유형 기본값 ('//'로 나눈 뒤)", "", "| 값 | 건수 |", "| --- | --- |"]
    counter = Counter(part for record in records for part in split_multi(record.get("지원유형")))
    lines += [f"| {shorten(value, 50)} | {count} |" for value, count in counter.most_common()]
    lines.append("")


def section_period_type(lines: list[str], records: list[dict]) -> None:
    lines += ["## 3-1. 신청기한유형 필드 (DATA-04)", ""]
    if not records or PERIOD_TYPE_KEY not in records[0]:
        lines += [f"'{PERIOD_TYPE_KEY}' 필드 없음", ""]
        return
    counter = Counter(str(r.get(PERIOD_TYPE_KEY)).strip() for r in records)
    lines += ["| 신청기한유형 | 건수 | 신청기한 분류 상위 3 |", "| --- | --- | --- |"]
    for value, count in counter.most_common():
        inner = Counter(classify_period(r.get(PERIOD_KEY)) for r in records if str(r.get(PERIOD_TYPE_KEY)).strip() == value)
        top = ", ".join(f"{label}({n})" for label, n in inner.most_common(3))
        lines.append(f"| {shorten(value, 30)} | {count} | {top} |")
    lines.append("")


def section_docs(lines: list[str], records: list[dict]) -> None:
    lines += ["## 5. 구비서류 형식 (DATA-07, BEN-03, ASST-04)", ""]
    for key in DOC_KEYS:
        values = [r.get(key) for r in records if is_filled(r.get(key))]
        lines.append(f"### {key} — {len(values)}건 ({len(values) / len(records) * 100:.1f}%)" if records else f"### {key}")
        lines.append("")
        if not values:
            lines += ["값 없음 또는 필드 없음", ""]
            continue
        markers = Counter()
        for value in values:
            for marker in ("○", "-", "·", "•", "\n", ",", "※", "1."):
                if marker in str(value):
                    markers["줄바꿈" if marker == "\n" else marker] += 1
        lines.append("구분 기호가 들어 있는 건수: " + ", ".join(f"'{m}' {c}" for m, c in markers.most_common()))
        lines += ["", "예시 5개", ""]
        lines += [f"- {shorten(v, 150)}" for v in values[:5]]
        lines.append("")


def section_categories(lines: list[str], data: dict[str, list[dict]]) -> None:
    lines += ["## 6. 분류 필드 값 (해시태그 사전 뼈대, D-22)", ""]
    records = data.get("serviceList", [])
    for key in CATEGORY_KEYS:
        counter = Counter(str(r.get(key)).strip() for r in records if is_filled(r.get(key)))
        if not counter:
            lines += [f"### {key}", "", "필드 없음", ""]
            continue
        lines += [f"### {key} ({len(counter)}종)", "", "| 값 | 건수 |", "| --- | --- |"]
        lines += [f"| {shorten(value, 50)} | {count} |" for value, count in counter.most_common(25)]
        lines.append("")


def section_modified(lines: list[str], records: list[dict]) -> None:
    lines += ["## 7. 수정일시 (증분 동기화 기준, DATA-01)", ""]
    raw = [r.get(MODIFIED_KEY) for r in records if is_filled(r.get(MODIFIED_KEY))]
    dates = [d for d in (parse_datetime(v) for v in raw) if d]
    if not dates:
        lines += [f"'{MODIFIED_KEY}' 값 없음 또는 형식 해석 실패", ""]
        return
    latest = max(dates)
    within = {days: sum(1 for d in dates if d >= latest - timedelta(days=days)) for days in (1, 7, 30)}
    lines += [
        f"- 원문 형식 예: `{raw[0]}`",
        f"- 가장 최근 수정: {latest:%Y-%m-%d %H:%M}",
        f"- 최근 1일 {within[1]}건, 7일 {within[7]}건({within[7] / len(records) * 100:.1f}%), 30일 {within[30]}건",
        "",
    ]


def build_report(raw_dir: Path = RAW_DIR) -> str:
    data = {endpoint: load_records(endpoint, raw_dir) for endpoint in ENDPOINTS}
    data = {endpoint: records for endpoint, records in data.items() if records}
    if not data:
        raise SystemExit(f"{raw_dir}에 수집된 파일이 없습니다. collect.py를 먼저 실행하세요.")

    lines = [
        "# 공공서비스(혜택) API 실측 보고서",
        "",
        f"생성: {datetime.now():%Y-%m-%d %H:%M} · analyze.py 자동 생성 (손으로 고치지 말고 다시 생성)",
        "",
    ]
    section_overview(lines, data, raw_dir)
    section_fill(lines, data)
    detail = data.get("serviceDetail") or data.get("serviceList", [])
    service_list = data.get("serviceList", [])
    targets = {record.get(ID_KEY): record for record in service_list}
    section_period(lines, detail)
    section_period_type(lines, detail)
    section_ja(lines, data.get("supportConditions", []))
    section_ja_groups(lines, data.get("supportConditions", []), targets)
    section_docs(lines, data.get("serviceDetail", []))
    section_categories(lines, data)
    section_targets(lines, service_list)
    section_split_values(lines, service_list)
    section_modified(lines, service_list)
    return "\n".join(lines)


def main() -> None:
    report = build_report()
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(report, encoding="utf-8")
    print(f"보고서 저장: {REPORT_PATH}")


if __name__ == "__main__":
    main()
