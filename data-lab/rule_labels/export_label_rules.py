"""검수한 판정 조건 라벨을 백엔드가 읽는 파일로 내보낸다 (D-93).

라벨 30개 서비스는 LLM 없이 수동 규칙(MANUAL)으로 benefit_rules에 들어간다. 동기화(DATA-01)가 이 파일을 읽어
공고 원문이 라벨을 만든 때와 같을 때만 넣는다(수정일시의 날짜 비교). 원문이 바뀌면 라벨이 낡았으므로 넣지 않는다.

실행 (레포 루트에서):
    python data-lab/rule_labels/check_labels.py           # 오류 0 확인 후
    python data-lab/rule_labels/export_label_rules.py     # backend/src/main/resources/eligibility/label-rules.csv 생성

labeler·memo는 빼고, 라벨을 만든 원문의 수정일(labeled_on, gold_set/samples.csv의 modified_at)을 붙인다.

LLM 조건 추출(DATA-03) 결과를 B가 검수한 줄(data-lab/conditions/reviewed_rules.csv)도 함께 내보낸다.
그 파일은 정답셋 밖 공고라 labeled_on 칸을 직접 가진다 (백엔드 검수 파일에서 그대로 옮긴 값).
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
DEFAULT_REVIEWED = REPO / "data-lab" / "conditions" / "reviewed_rules.csv"
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
        own = (row.get("labeled_on") or "").strip()[:10]
        if not own and sid not in labeled_on:
            raise SystemExit(f"{sid}: samples.csv에 없는 서비스라 원문 수정일을 알 수 없습니다")
        rows.append({
            "service_id": sid,
            "labeled_on": own or labeled_on[sid],
            "group": row["group"].strip(),
            "field": row["field"].strip(),
            "operator": row["operator"].strip(),
            "values": row["values"].strip(),
            "source_quote": row["source_quote"].strip(),
            "question": row.get("question", "").strip(),
        })
    return rows


def merge(labels: list[dict[str, str]], reviewed: list[dict[str, str]]) -> list[dict[str, str]]:
    """정답셋 라벨 + 검수한 LLM 줄. 한 공고가 양쪽에 있으면 어느 쪽이 맞는지 모르므로 멈춘다."""
    gold = {r["service_id"].strip() for r in labels}
    both = sorted({r["service_id"].strip() for r in reviewed} & gold)
    if both:
        raise SystemExit(f"정답셋 라벨과 검수 파일에 모두 있는 서비스: {', '.join(both)}")
    for r in reviewed:
        if not (r.get("labeled_on") or "").strip():
            raise SystemExit(f"{r['service_id']}: 검수 파일 줄에 labeled_on이 없습니다")
    return labels + reviewed


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
    p.add_argument("--reviewed", type=Path, default=DEFAULT_REVIEWED, help="검수한 LLM 줄 (없으면 건너뜀)")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = p.parse_args()
    reviewed = read_csv(args.reviewed) if args.reviewed.exists() else []
    rows = export(merge(read_csv(args.labels), reviewed), read_csv(args.samples))
    write(rows, args.out)
    print(f"{len(rows)}줄, 서비스 {len({r['service_id'] for r in rows})}개 → {args.out}")


if __name__ == "__main__":
    sys.exit(main())
