"""검수한 판정 조건 라벨을 백엔드가 읽는 파일로 내보낸다 (D-93).

라벨 30개 서비스는 LLM 없이 수동 규칙(MANUAL)으로 benefit_rules에 들어간다. 동기화(DATA-01)가 이 파일을 읽어
공고 원문이 라벨을 만든 때와 같을 때만 넣는다(수정일시의 날짜 비교). 원문이 바뀌면 라벨이 낡았으므로 넣지 않는다.

실행 (레포 루트에서):
    python data-lab/rule_labels/check_labels.py           # 오류 0 확인 후
    python data-lab/rule_labels/export_label_rules.py     # backend/src/main/resources/eligibility/label-rules.csv 생성

labeler·memo는 빼고, 라벨을 만든 원문의 수정일(labeled_on, gold_set/samples.csv의 modified_at)을 붙인다.
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
DEFAULT_LABELS = HERE / "rule_labels.csv"
DEFAULT_SAMPLES = HERE.parent / "gold_set" / "samples.csv"
DEFAULT_OUT = REPO / "backend" / "src" / "main" / "resources" / "eligibility" / "label-rules.csv"
OUT_COLUMNS = ["service_id", "labeled_on", "group", "field", "operator", "values", "source_quote", "question"]


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def export(labels: list[dict[str, str]], samples: list[dict[str, str]]) -> list[dict[str, str]]:
    labeled_on = {s["service_id"].strip(): s["modified_at"].strip()[:10] for s in samples}
    rows = []
    for row in labels:
        sid = row["service_id"].strip()
        if sid not in labeled_on:
            raise SystemExit(f"{sid}: samples.csv에 없는 서비스라 원문 수정일을 알 수 없습니다")
        rows.append({
            "service_id": sid,
            "labeled_on": labeled_on[sid],
            "group": row["group"].strip(),
            "field": row["field"].strip(),
            "operator": row["operator"].strip(),
            "values": row["values"].strip(),
            "source_quote": row["source_quote"].strip(),
            "question": row.get("question", "").strip(),
        })
    return rows


def write(rows: list[dict[str, str]], out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=OUT_COLUMNS, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)


def main() -> None:
    p = argparse.ArgumentParser(description="판정 조건 라벨 → 백엔드 수동 규칙 파일")
    p.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    p.add_argument("--samples", type=Path, default=DEFAULT_SAMPLES)
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = p.parse_args()
    rows = export(read_csv(args.labels), read_csv(args.samples))
    write(rows, args.out)
    print(f"{len(rows)}줄, 서비스 {len({r['service_id'] for r in rows})}개 → {args.out}")


if __name__ == "__main__":
    sys.exit(main())
