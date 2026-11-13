"""지역 판정 방식 결정을 위한 소관기관명·코드 점검.

사용법 (레포 루트에서, collect.py로 받은 원본이 있어야 함):
    python data-lab/api_survey/region_check.py

결과: data-lab/reports/region_check.md
판단 기준: 지자체 소관 공고의 기관명만으로 시·도와 시·군·구를 정확히 알 수 있는가
  - 기관명에 시·도 이름이 들어 있는가 ("부산광역시 부산진구" vs "부산진구")
  - 시·도 없이 같은 이름이 여러 지역에 있는가 ("중구", "동구" 등)
  - 기관명과 기관코드가 1:1인가
"""
from __future__ import annotations

import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from analyze import is_filled, load_records, shorten  # noqa: E402
from common import DATA_LAB, RAW_DIR  # noqa: E402

REPORT = DATA_LAB / "reports" / "region_check.md"
NAME_KEY, CODE_KEY, TYPE_KEY = "소관기관명", "소관기관코드", "소관기관유형"
LOCAL_TYPES = ("시군구", "광역시도", "교육청", "지방공기업", "지방출자_출연기관")

# (정식 이름, 줄임·옛 이름들)
SIDO = [
    ("서울특별시", ("서울",)), ("부산광역시", ("부산",)), ("대구광역시", ("대구",)), ("인천광역시", ("인천",)),
    ("대전광역시", ("대전",)), ("울산광역시", ("울산",)), ("세종특별자치시", ("세종",)),
    ("경기도", ("경기",)), ("강원특별자치도", ("강원도", "강원")), ("충청북도", ("충북",)), ("충청남도", ("충남",)),
    ("전북특별자치도", ("전라북도", "전북")), ("경상북도", ("경북",)), ("경상남도", ("경남",)),
    # 2026-07-01 전라남도·광주광역시 통합. 옛 이름은 새 이름으로 본다
    ("전남광주통합특별시", ("전라남도", "광주광역시", "전남")),
    ("제주특별자치도", ("제주도", "제주")),
]
SIGUNGU_PATTERN = re.compile(r"([가-힣]+(시|군|구))")


def find_sido(name: str) -> tuple[str | None, str]:
    """기관명 앞부분에서 시·도를 찾는다. (정식 이름, 'full'|'short'|'none')"""
    text = name.strip()
    for full, shorts in SIDO:
        if text.startswith(full):
            return full, "full"
    for full, shorts in SIDO:
        for short in shorts:
            # 줄임말 뒤에는 띄어쓰기나 끝이 와야 한다 ("부산진구"를 부산으로, "광주시"(경기)를 광주광역시로 잘못 읽지 않게)
            if text == short or text.startswith(short + " "):
                return full, "short"
    return None, "none"


def sigungu_part(name: str, sido: str | None) -> str | None:
    """시·도 뒤(또는 이름 전체)에서 첫 시·군·구 이름을 뽑는다."""
    text = name.strip()
    if sido:
        for full, shorts in SIDO:
            if full == sido:
                for prefix in (full, *shorts):
                    if text.startswith(prefix):
                        text = text[len(prefix):].strip()
                        break
    match = SIGUNGU_PATTERN.match(text)
    return match.group(1) if match else None


REGION_TYPES = ("시군구", "광역시도")
NATIONWIDE_TYPES = ("중앙행정기관", "공공기관")


def code_region_table(records: list[dict]) -> dict[str, tuple[str, str | None]]:
    """시군구·광역시도 공고의 기관명으로 '기관코드 → (시·도, 시·군·구)' 표를 만든다.
    지방공기업·출연기관은 상위 지자체와 같은 코드를 쓰므로 이 표로 지역을 찾는다."""
    table: dict[str, tuple[str, str | None]] = {}
    for record in records:
        if str(record.get(TYPE_KEY)).strip() not in REGION_TYPES:
            continue
        name, code = str(record.get(NAME_KEY)).strip(), str(record.get(CODE_KEY)).strip()
        sido, _ = find_sido(name)
        if sido:
            table[code] = (sido, sigungu_part(name, sido))
    return table


def resolve_region(record: dict, table: dict[str, tuple[str, str | None]]) -> tuple[str, str]:
    """공고 하나의 지역과 찾은 방법. ('전국'|'시·도 시·군·구', 'nationwide'|'code'|'name'|'unknown')"""
    org_type = str(record.get(TYPE_KEY)).strip()
    if org_type in NATIONWIDE_TYPES:
        return "전국", "nationwide"
    code = str(record.get(CODE_KEY)).strip()
    if code in table:
        sido, sigungu = table[code]
        return f"{sido} {sigungu or ''}".strip(), "code"
    sido, _ = find_sido(str(record.get(NAME_KEY)))
    if sido:
        sigungu = sigungu_part(str(record.get(NAME_KEY)), sido)
        return f"{sido} {sigungu or ''}".strip(), "name"
    return "", "unknown"


def build_report(raw_dir: Path = RAW_DIR) -> str:
    records = load_records("serviceList", raw_dir)
    if not records:
        raise SystemExit("serviceList 원본이 없습니다. collect.py를 먼저 실행하세요.")
    lines = [
        "# 지역 판정 점검 (소관기관명·코드)",
        "",
        f"생성: {datetime.now():%Y-%m-%d %H:%M} · region_check.py 자동 생성",
        "",
        "## 1. 소관기관유형별 기관명 형식",
        "",
        "| 유형 | 공고 수 | 기관 수 | 시·도 정식명으로 시작 | 시·도 줄임말로 시작 | 시·도 없음 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    by_type: dict[str, list[dict]] = defaultdict(list)
    for record in records:
        by_type[str(record.get(TYPE_KEY)).strip()].append(record)

    no_sido_examples: dict[str, Counter] = {}
    for org_type, items in sorted(by_type.items(), key=lambda kv: -len(kv[1])):
        names = {str(r.get(NAME_KEY)).strip() for r in items if is_filled(r.get(NAME_KEY))}
        kinds = Counter(find_sido(name)[1] for name in names)
        lines.append(
            f"| {org_type} | {len(items)} | {len(names)} | {kinds['full']} | {kinds['short']} | {kinds['none']} |"
        )
        no_sido_examples[org_type] = Counter(
            str(r.get(NAME_KEY)).strip() for r in items if find_sido(str(r.get(NAME_KEY)))[1] == "none"
        )

    lines += ["", "## 2. 시·도 없이 적힌 기관명 (지자체 유형, 공고 수 상위 15)", ""]
    for org_type in LOCAL_TYPES:
        counter = no_sido_examples.get(org_type, Counter())
        if not counter:
            lines += [f"- {org_type}: 없음"]
            continue
        top = ", ".join(f"{shorten(name, 25)}({count})" for name, count in counter.most_common(15))
        lines += [f"- {org_type} ({len(counter)}개 기관): {top}"]

    # 시군구: 시·도 없이 시·군·구 이름만으로는 겹치는 경우
    lines += ["", "## 3. 시군구 기관: 이름이 여러 지역에 겹치는지", ""]
    sigungu_records = by_type.get("시군구", [])
    sido_by_sigungu: dict[str, set[str]] = defaultdict(set)
    unparsed = Counter()
    for record in sigungu_records:
        name = str(record.get(NAME_KEY)).strip()
        sido, _ = find_sido(name)
        part = sigungu_part(name, sido)
        if part is None:
            unparsed[name] += 1
            continue
        sido_by_sigungu[part].add(sido or "(시·도 없음)")
    duplicated = {part: sidos for part, sidos in sido_by_sigungu.items() if len(sidos) > 1}
    lines += [
        f"- 시·군·구 이름을 뽑은 고유 이름: {len(sido_by_sigungu)}개, 뽑지 못한 기관명: {len(unparsed)}개",
        f"- 시·도가 달라도 이름이 같은 시·군·구: {len(duplicated)}개 "
        + ", ".join(f"{part}({len(sidos)}곳)" for part, sidos in sorted(duplicated.items(), key=lambda kv: -len(kv[1]))[:15]),
    ]
    if unparsed:
        lines.append("- 뽑지 못한 기관명 예: " + ", ".join(f"{shorten(n, 25)}({c})" for n, c in unparsed.most_common(10)))

    # 기관명 ↔ 코드
    lines += ["", "## 4. 기관명과 기관코드", ""]
    codes_by_name: dict[str, set[str]] = defaultdict(set)
    names_by_code: dict[str, set[str]] = defaultdict(set)
    code_lengths = Counter()
    for record in records:
        name, code = str(record.get(NAME_KEY)).strip(), str(record.get(CODE_KEY)).strip()
        codes_by_name[name].add(code)
        names_by_code[code].add(name)
        code_lengths[len(code)] += 1
    multi_code = {n: c for n, c in codes_by_name.items() if len(c) > 1}
    multi_name = {c: n for c, n in names_by_code.items() if len(n) > 1}
    lines += [
        f"- 코드 길이 분포: " + ", ".join(f"{length}자리({count})" for length, count in code_lengths.most_common()),
        f"- 이름 하나에 코드 여러 개: {len(multi_code)}개 / 코드 하나에 이름 여러 개: {len(multi_name)}개",
    ]
    if multi_name:
        lines.append(
            "- 코드 하나에 이름 여러 개 예: "
            + "; ".join(f"{code}: {', '.join(sorted(names)[:3])}" for code, names in list(multi_name.items())[:5])
        )
    lines += ["", "| 유형 | 코드 첫 자리 분포 |", "| --- | --- |"]
    for org_type, items in sorted(by_type.items(), key=lambda kv: -len(kv[1])):
        first = Counter(str(r.get(CODE_KEY)).strip()[:1] for r in items)
        lines.append(f"| {org_type} | " + ", ".join(f"{d}({c})" for d, c in first.most_common(5)) + " |")
    lines.append("")

    # 5. 기관코드 기준 지역 찾기 결과
    table = code_region_table(records)
    lines += [
        "## 5. 기관코드로 지역 찾기 (코드 → 지역 표는 시군구·광역시도 공고 기관명으로 자동 생성)",
        "",
        f"- 표에 들어간 기관코드: {len(table)}개",
        "",
        "| 유형 | 공고 수 | 전국 | 코드로 찾음 | 이름으로 찾음 | 못 찾음 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    unknown = Counter()
    for org_type, items in sorted(by_type.items(), key=lambda kv: -len(kv[1])):
        methods = Counter()
        for record in items:
            _, method = resolve_region(record, table)
            methods[method] += 1
            if method == "unknown":
                unknown[f"{record.get(NAME_KEY)} ({record.get(CODE_KEY)})"] += 1
        lines.append(
            f"| {org_type} | {len(items)} | {methods['nationwide']} | {methods['code']} | {methods['name']} | {methods['unknown']} |"
        )
    if unknown:
        lines += ["", "못 찾은 기관 (공고 수 상위 20): " + ", ".join(f"{shorten(n, 30)}({c})" for n, c in unknown.most_common(20))]
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(build_report(), encoding="utf-8")
    print(f"보고서 저장: {REPORT}")


if __name__ == "__main__":
    main()
