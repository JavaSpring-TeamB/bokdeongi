"""정답셋 표본 30건 추출 (C, DATA-04~07·09 정확도 측정용).

사용법 (레포 루트에서):
    python data-lab/gold_set/sample.py              # samples.csv, labels.csv 생성
    python data-lab/gold_set/sample.py --size 30 --seed 20261112

- 입력: api_survey/collect.py가 받은 원본 (data-lab/data/raw/gov24/, 커밋 안 됨)
- 신청기한 분류(api_survey 보고서 3장)별로 정해진 개수를 뽑고, 같은 분류 안에서는 지원유형이 겹치지 않게 고른다
- 개인·가구 대상 혜택만 뽑는다 (개인 회원 추천 대상)
- 시연 시나리오 '청년 월세' 공고는 반드시 넣는다
- labels.csv가 이미 있으면 덮어쓰지 않는다 (라벨 작업 보호). 다시 만들려면 직접 지운다
- 외부 라이브러리 없이 표준 라이브러리만 쓴다. CSV는 엑셀에서 바로 열리도록 UTF-8 BOM으로 저장한다
"""
from __future__ import annotations

import argparse
import csv
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "api_survey"))
from analyze import classify_period, is_filled, is_personal, load_records, split_multi  # noqa: E402
from common import RAW_DIR  # noqa: E402

SAMPLES_PATH = HERE / "samples.csv"
LABELS_PATH = HERE / "labels.csv"

# 신청기한 분류별 표본 수 (합 30). 실측 분포를 따르되 드문 유형도 최소 1~2건 넣는다
QUOTAS: dict[str, int] = {
    "날짜(연도 포함)": 6,
    "상시": 5,
    "매년 반복(월 단위)": 4,
    "기관별 상이": 3,
    "공고·문의 참조": 2,
    "상대 기간": 2,
    "신청 불필요": 2,
    "날짜(연도 없음)": 2,
    "기타": 2,
    "예산 소진·선착순": 2,
}

# 반드시 넣을 시연 공고: 서비스명에 모든 단어가 들어 있는 것 중 첫 번째
MUST_INCLUDE_KEYWORDS: list[tuple[str, ...]] = [("청년", "월세")]

ID_KEY = "서비스ID"
INPUT_COLUMNS = [
    ("service_id", "서비스ID"),
    ("title", "서비스명"),
    ("period_class", None),
    ("period_type", "신청기한유형"),
    ("period_text", "신청기한"),
    ("support_type", "지원유형"),
    ("service_field", "서비스분야"),
    ("user_type", "사용자구분"),
    ("org_name", "소관기관명"),
    ("target_text", "지원대상"),
    ("criteria_text", "선정기준"),
    ("content_text", "지원내용"),
    ("apply_method_text", "신청방법"),
    ("required_docs_text", "구비서류"),
    ("official_docs_text", "공무원확인구비서류"),
    ("self_docs_text", "본인확인필요구비서류"),
    ("online_apply_url", "온라인신청사이트URL"),
]

# 사람이 채우는 정답 칸. 값 규칙은 README의 라벨링 가이드를 따른다
LABEL_COLUMNS = [
    "apply_start", "apply_end", "result_date", "date_status", "date_precision", "recurring_yearly", "date_source_quote",
    "max_amount", "amount_period", "annual_amount", "amount_source_quote",
    "exclusion_text",
    "self_docs",
    "hashtags",
    "labeler", "memo",
]


def merge_records(raw_dir: Path = RAW_DIR) -> list[dict]:
    """serviceList와 serviceDetail을 서비스ID로 합친다. 같은 필드는 상세 값을 우선한다."""
    listing = {r.get(ID_KEY): r for r in load_records("serviceList", raw_dir)}
    details = {r.get(ID_KEY): r for r in load_records("serviceDetail", raw_dir)}
    merged = []
    for service_id, base in listing.items():
        record = dict(base)
        for key, value in details.get(service_id, {}).items():
            if is_filled(value):
                record[key] = value
        merged.append(record)
    if not merged:
        raise SystemExit(f"{raw_dir}에 수집된 파일이 없습니다. python data-lab/api_survey/collect.py를 먼저 실행하세요.")
    return merged


def main_support_type(record: dict) -> str:
    types = split_multi(record.get("지원유형"))
    return types[0] if types else "(없음)"


def pick_diverse(candidates: list[dict], count: int, rng: random.Random, used_types: set[str]) -> list[dict]:
    """지원유형이 겹치지 않게 먼저 고르고, 모자라면 나머지에서 채운다."""
    pool = candidates[:]
    rng.shuffle(pool)
    chosen: list[dict] = []
    for record in pool:
        if len(chosen) == count:
            break
        support = main_support_type(record)
        if support not in used_types:
            chosen.append(record)
            used_types.add(support)
    for record in pool:
        if len(chosen) == count:
            break
        if record not in chosen:
            chosen.append(record)
    return chosen


def find_must_include(records: list[dict]) -> list[dict]:
    found = []
    for keywords in MUST_INCLUDE_KEYWORDS:
        matches = [r for r in records if all(k in str(r.get("서비스명", "")) for k in keywords)]
        if not matches:
            print(f"경고: 서비스명에 {keywords}가 모두 들어간 공고가 없습니다.")
            continue
        # 중앙행정기관 공고를 먼저, 그다음 서비스ID 순 (실행할 때마다 같은 공고가 뽑히게)
        matches.sort(key=lambda r: (r.get("소관기관유형") != "중앙행정기관", str(r.get(ID_KEY))))
        found.append(matches[0])
    return found


def sample(records: list[dict], size: int, seed: int) -> list[dict]:
    rng = random.Random(seed)
    personal = [r for r in records if is_personal(r)]
    by_class: dict[str, list[dict]] = {}
    for record in personal:
        by_class.setdefault(classify_period(record.get("신청기한")), []).append(record)

    quotas = dict(QUOTAS)
    if sum(quotas.values()) != size:
        # 크기를 바꾸면 비율대로 다시 나눈다 (분류마다 최소 1건)
        total = sum(QUOTAS.values())
        quotas = {k: max(1, round(v * size / total)) for k, v in QUOTAS.items()}

    chosen: list[dict] = []
    must = find_must_include(personal)
    for record in must:
        label = classify_period(record.get("신청기한"))
        quotas[label] = max(0, quotas.get(label, 0) - 1)
        chosen.append(record)

    used_types = {main_support_type(r) for r in chosen}
    chosen_ids = {r.get(ID_KEY) for r in chosen}
    for label, count in quotas.items():
        candidates = [r for r in by_class.get(label, []) if r.get(ID_KEY) not in chosen_ids]
        if len(candidates) < count:
            print(f"경고: '{label}' 후보가 {len(candidates)}건뿐이라 {count}건을 다 못 뽑았습니다.")
        picked = pick_diverse(candidates, count, rng, used_types)
        chosen.extend(picked)
        chosen_ids.update(r.get(ID_KEY) for r in picked)
    return chosen


def to_row(record: dict) -> dict[str, str]:
    row = {}
    for column, key in INPUT_COLUMNS:
        if key is None:
            row[column] = classify_period(record.get("신청기한"))
        else:
            value = record.get(key)
            row[column] = str(value).strip() if is_filled(value) else ""
    return row


def write_csv(path: Path, columns: list[str], rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description="정답셋 표본 추출")
    parser.add_argument("--size", type=int, default=30)
    parser.add_argument("--seed", type=int, default=20261112, help="같은 seed면 항상 같은 표본")
    args = parser.parse_args()

    rows = [to_row(r) for r in sample(merge_records(), args.size, args.seed)]
    write_csv(SAMPLES_PATH, [c for c, _ in INPUT_COLUMNS], rows)
    print(f"표본 {len(rows)}건 → {SAMPLES_PATH}")

    if LABELS_PATH.exists():
        print(f"{LABELS_PATH}가 이미 있어서 덮어쓰지 않았습니다.")
    else:
        label_rows = [{"service_id": r["service_id"], "title": r["title"], "period_class": r["period_class"],
                       **{c: "" for c in LABEL_COLUMNS}} for r in rows]
        write_csv(LABELS_PATH, ["service_id", "title", "period_class", *LABEL_COLUMNS], label_rows)
        print(f"라벨 양식 → {LABELS_PATH}")


if __name__ == "__main__":
    main()
