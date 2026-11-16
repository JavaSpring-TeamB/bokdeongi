"""신청 기간이 이미 지난 공고 점검.

공공서비스 API에는 오래전에 끝난 사업도 그대로 남아 있다 (예: 신청기한 2023.02.02~2023.04.28인데 수정일시는 2026년).
신청기한·지원대상 원문의 날짜와 연도를 읽어 공고를 나누고 건수를 보고서로 남긴다.

    PAST_OLD      신청 기간이 작년 이전에 끝남 (예: 2023.02.02~2023.04.28)
    PAST_THIS     올해 신청 기간이 이미 끝났고, 반복 표시나 다음 기간이 없음
    RECURRING     '매년', '해마다' 등 반복 표시가 있음 → 다음 접수 예상 시기로 안내 가능
    OPEN          기간이 진행 중이거나 앞으로 있음
    NO_DATE       상시·기관별 상이·신청 불필요·'○○일로부터 N년 이내'·날짜 없음 (기간으로 판단 안 함)
    STALE_TEXT    (보조 표시) 지원대상 원문에 나온 연도가 모두 작년 이전 (예: '2024년 2월 졸업예정자')

사용법 (레포 루트에서, collect.py로 원본을 받아 둔 뒤):
    python data-lab/api_survey/stale_check.py                       # data/raw/gov24 원본 전체
    python data-lab/api_survey/stale_check.py --samples data-lab/gold_set/samples.csv
    python data-lab/api_survey/stale_check.py --today 2026-11-16
결과: data-lab/reports/stale_check.md (건수와 예시만, 공개 공고 데이터)
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "validation"))
from common import RAW_DIR  # noqa: E402
from validator import extract_dates  # noqa: E402

REPORT = HERE.parent / "reports" / "stale_check.md"

RECURRING = re.compile(r"매년|해마다|매월|매분기|분기별|매 ?학기|연\s*\d+\s*회")
NO_DATE_TYPES = {"상시신청", "접수기관별상이"}
NO_DATE_TEXT = re.compile(r"상시|수시|연중|신청\s*불필요|신청\s*절차(가)?\s*없|별도\s*신청\s*없|상이")
# '사유 발생일로부터 5년 이내'처럼 사람마다 기준일이 다른 기간. 괄호 속 연도는 법 시행일 등이라 마감이 아니다
RELATIVE = re.compile(r"(로부터|부터)\s*\d+\s*(일|주|개월|년)|\d+\s*(일|주|개월|년)\s*이내")
YEAR = re.compile(r"(?<!\d)((?:19|20)\d{2})\s*(?:년|\.|-|/)")


@dataclass(frozen=True)
class Record:
    service_id: str
    title: str
    period_type: str
    period_text: str
    target_text: str
    modified_year: int | None


@dataclass(frozen=True)
class Verdict:
    label: str
    stale_text: bool
    last_date: date | None


def _last_day(year: int, month: int) -> int:
    nxt = date(year + (month == 12), month % 12 + 1, 1)
    return (nxt - date(year, month, 1)).days


def mentioned_dates(text: str, base_year: int | None) -> list[date]:
    """원문 날짜를 날짜로 바꾼다. 일이 없으면 그달 말일, 연도를 모르면 버린다."""
    out = []
    for m in extract_dates(text, base_year):
        if m.year is None:
            continue
        day = m.day if m.day is not None else _last_day(m.year, m.month)
        try:
            out.append(date(m.year, m.month, min(day, _last_day(m.year, m.month))))
        except ValueError:
            continue
    return out


def years_in(text: str) -> list[int]:
    return [int(y) for y in YEAR.findall(text or "")]


def classify(record: Record, today: date) -> Verdict:
    text = record.period_text or ""
    target_years = years_in(record.target_text)
    stale_text = bool(target_years) and max(target_years) < today.year

    if RECURRING.search(text):
        return Verdict("RECURRING", stale_text, None)
    if record.period_type in NO_DATE_TYPES or NO_DATE_TEXT.search(text) or RELATIVE.search(text):
        return Verdict("NO_DATE", stale_text, None)

    # 연도가 적힌 날짜만 믿는다. 연도 없는 날짜는 수정 연도로 추정하되, 연도 표기가 하나도 없으면 판단하지 않는다
    explicit_years = years_in(text)
    dates = mentioned_dates(text, record.modified_year if explicit_years else None)
    if not dates:
        if explicit_years and max(explicit_years) < today.year:
            return Verdict("PAST_OLD", stale_text, None)          # '2025년 상반기'처럼 날짜 없이 연도만
        if explicit_years and max(explicit_years) == today.year and _half_passed(text, today):
            return Verdict("PAST_THIS", stale_text, None)         # '2026년 상반기' (7월 이후)
        return Verdict("NO_DATE", stale_text, None)
    last = max(dates)
    if last >= today:
        return Verdict("OPEN", stale_text, last)
    if last.year < today.year:
        return Verdict("PAST_OLD", stale_text, last)
    return Verdict("PAST_THIS", stale_text, last)


def _half_passed(text: str, today: date) -> bool:
    return "상반기" in text and "하반기" not in text and today.month >= 7


# ---------------------------------------------------------------- 읽기

def _year(value: str | None) -> int | None:
    m = re.match(r"\s*((?:19|20)\d{2})", value or "")
    return int(m.group(1)) if m else None


def from_raw(raw_dir: Path = RAW_DIR) -> list[Record]:
    import json
    records = []
    for path in sorted((raw_dir / "serviceDetail").glob("page_*.json")):
        for r in json.loads(path.read_text(encoding="utf-8")).get("data", []):
            records.append(Record(str(r.get("서비스ID", "")), str(r.get("서비스명", "")), str(r.get("신청기한유형", "") or ""),
                                  str(r.get("신청기한", "") or ""), str(r.get("지원대상", "") or ""),
                                  _year(str(r.get("수정일시", "")))))
    return records


def from_samples(path: Path) -> list[Record]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return [Record(r["service_id"], r["title"], r.get("period_type", ""), r.get("period_text", ""),
                       r.get("target_text", ""), _year(r.get("modified_at"))) for r in csv.DictReader(f)]


# ---------------------------------------------------------------- 보고서

LABELS = ["PAST_OLD", "PAST_THIS", "RECURRING", "OPEN", "NO_DATE"]


def report(records: list[Record], today: date, source: str) -> str:
    verdicts = [(r, classify(r, today)) for r in records]
    counts = Counter(v.label for _, v in verdicts)
    stale_text = sum(1 for _, v in verdicts if v.stale_text)
    lines = [
        "# 신청 기간 점검 (stale_check.py 자동 생성)", "",
        f"- 기준일: {today.isoformat()} · 원본: {source} · 공고 {len(records)}건", "",
        "| 분류 | 건수 | 비율 |", "| --- | --- | --- |",
    ]
    for label in LABELS:
        n = counts.get(label, 0)
        lines.append(f"| {label} | {n} | {n / len(records):.1%} |" if records else f"| {label} | 0 | - |")
    lines += ["", f"- 보조 표시 STALE_TEXT(지원대상에 나온 연도가 모두 작년 이전): {stale_text}건", ""]
    for label in ["PAST_OLD", "PAST_THIS"]:
        rows = [(r, v) for r, v in verdicts if v.label == label][:15]
        if rows:
            lines += [f"## {label} 예시 (최대 15건)", "", "| 서비스ID | 서비스명 | 신청기한 |", "| --- | --- | --- |"]
            lines += [f"| {r.service_id} | {r.title[:30]} | {r.period_text.replace(chr(10), ' ')[:60]} |" for r, _ in rows]
            lines.append("")
    rows = [(r, v) for r, v in verdicts if v.stale_text and v.label not in ("PAST_OLD",)][:15]
    if rows:
        lines += ["## STALE_TEXT인데 기간으로는 안 걸린 공고 (최대 15건)", "", "| 서비스ID | 서비스명 | 분류 | 지원대상 앞부분 |",
                  "| --- | --- | --- | --- |"]
        lines += [f"| {r.service_id} | {r.title[:30]} | {v.label} | {r.target_text.replace(chr(10), ' ')[:60]} |"
                  for r, v in rows]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--samples", type=Path, help="원본 대신 표본 CSV(gold_set/samples.csv)로 점검")
    parser.add_argument("--today", type=date.fromisoformat, default=date.today())
    parser.add_argument("--out", type=Path, default=REPORT)
    args = parser.parse_args()
    records = from_samples(args.samples) if args.samples else from_raw()
    if not records:
        sys.exit(f"원본이 없음: {RAW_DIR}/serviceDetail (collect.py를 먼저 실행)")
    text = report(records, args.today, str(args.samples or RAW_DIR))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
