"""정답셋(C의 사람 라벨)을 검증 모듈에 통과시켜 '맞는 값을 잘못 버리는지' 확인한다.

사람이 원문을 보고 적은 값은 모두 살아남아야 한다. 버려지는 값이 있으면
(1) 검증 모듈이 너무 엄격하거나 (2) 라벨이 원문과 다르거나 둘 중 하나다.

실행 (레포 루트에서):
    python data-lab/validation/check_gold.py
    python data-lab/validation/check_gold.py --wrong     # 일부러 틀린 값을 넣어 잡아내는 비율
    python data-lab/validation/check_gold.py --gold-dir 다른/경로
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from validator import Item, Purpose, validate  # noqa: E402

DEFAULT_GOLD = Path(__file__).resolve().parent.parent / "gold_set"
SOURCE_FIELDS = ("title", "period_text", "target_text", "criteria_text", "content_text",
                 "apply_method_text", "required_docs_text", "official_docs_text", "self_docs_text")


def base_year(label: dict[str, str]) -> int | None:
    """라벨의 posted_year(공고 등록·수정 연도). 칸이 없으면 연도 추정을 확인하지 않는다."""
    value = (label.get("posted_year") or "").strip()
    return int(value) if value.isdigit() else None


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def items_from_label(label: dict[str, str]) -> list[Item]:
    """라벨 한 행에서 검증할 값을 만든다. 연간 환산(annual_amount)은 계산값이라 뺀다."""
    items = []
    dates = [d for key in ("apply_start", "apply_end", "result_date")
             for d in (label.get(key) or "").split(";") if d.strip()]
    if label.get("date_source_quote") and dates:
        items.append(Item("date", label["date_source_quote"], dates=dates))
    if label.get("amount_source_quote") and label.get("max_amount"):
        items.append(Item("max_amount", label["amount_source_quote"], numbers=[label["max_amount"]]))
    if label.get("exclusion_text"):
        items.append(Item("exclusion_text", label["exclusion_text"]))
    return items


def wrong_variants(item: Item) -> list[tuple[str, Item]]:
    """사람 라벨을 일부러 틀리게 바꾼 값들. 검증 모듈이 전부 버려야 한다."""
    variants = []
    for i, raw in enumerate(item.numbers):
        for name, bad in (("금액×10", int(raw) * 10), ("금액+1000", int(raw) + 1000)):
            numbers = list(item.numbers)
            numbers[i] = str(bad)
            variants.append((name, Item(item.field, item.source_quote, numbers=numbers)))
    for i, date in enumerate(item.dates):
        parts = [int(p) for p in date.split("-")]
        shifted = {"연도-1": [parts[0] - 1, *parts[1:]]}
        if len(parts) == 3:
            shifted["일+1"] = [parts[0], parts[1], parts[2] % 28 + 1]
        shifted["월+1"] = [parts[0], parts[1] % 12 + 1, *parts[2:]]
        for name, new in shifted.items():
            dates = list(item.dates)
            dates[i] = "-".join(f"{p:02d}" if k else str(p) for k, p in enumerate(new))
            variants.append((name, Item(item.field, item.source_quote, dates=dates)))
    if item.source_quote:
        variants.append(("근거 지어냄", Item(item.field, item.source_quote + " 추가 지원",
                                              numbers=item.numbers, dates=item.dates)))
    return variants


def run_wrong(gold_dir: Path) -> int:
    """틀린 값을 얼마나 잡아내는지 잰다."""
    samples = {row["service_id"]: row for row in read_csv(gold_dir / "samples.csv")}
    total = caught = 0
    for label in read_csv(gold_dir / "labels.csv"):
        sources = [samples[label["service_id"]].get(name) for name in SOURCE_FIELDS]
        for item in items_from_label(label):
            for name, bad in wrong_variants(item):
                total += 1
                if validate(bad, Purpose.EXTRACT, sources, base_year=base_year(label)).dropped:
                    caught += 1
                else:
                    print(f"[놓침] {label['service_id']} / {bad.field} {name}: {bad.numbers or bad.dates}")
    print(f"\n틀린 값 {total}개 중 {caught}개 버림 ({caught / total:.0%})" if total else "틀린 값 없음")
    return 0  # 측정용이라 실패 코드로 끝내지 않는다


def run(gold_dir: Path) -> int:
    samples = {row["service_id"]: row for row in read_csv(gold_dir / "samples.csv")}
    labels = read_csv(gold_dir / "labels.csv")
    checked = dropped = inferred = 0
    for label in labels:
        sample = samples.get(label["service_id"])
        if sample is None:
            print(f"[표본 없음] {label['service_id']}")
            dropped += 1
            continue
        sources = [sample.get(name) for name in SOURCE_FIELDS]
        for item in items_from_label(label):
            checked += 1
            result = validate(item, Purpose.EXTRACT, sources, base_year=base_year(label))
            if result.dropped:
                dropped += 1
                print(f"[버림] {label['service_id']} {label['title'][:20]} / {item.field}: {result.reason}")
                print(f"       근거: {item.source_quote!r}")
            elif any("추정" in note for note in result.notes):
                inferred += 1
                print(f"[연도 추정] {label['service_id']} / {item.field}: {'; '.join(result.notes)}")
    print(f"\n라벨 {len(labels)}행, 검증한 값 {checked}개, 버림 {dropped}개, 연도 추정 {inferred}개")
    return 1 if dropped else 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--gold-dir", type=Path, default=DEFAULT_GOLD)
    parser.add_argument("--wrong", action="store_true", help="라벨을 일부러 틀리게 바꿔서 잡아내는 비율을 잰다")
    args = parser.parse_args()
    sys.exit(run_wrong(args.gold_dir) if args.wrong else run(args.gold_dir))


if __name__ == "__main__":
    main()
