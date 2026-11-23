"""LLM 조건 추출 검수 도구 (DATA-03, D-99).

백엔드가 쓴 검수 파일(backend/conditions-review/conditions-review-*.csv)을 검수 초안으로 바꾸고,
검수한 reviewed_rules.csv의 형식을 검사한다. 근거 문구가 원문에 있는지는 백엔드가 이미 봤으므로(D-59)
여기서는 형식(필드·연산자·값·질문)과 값의 숫자가 근거 문구 안에 있는지만 본다.

실행 (레포 루트에서):
    python data-lab/conditions/review_tool.py draft backend/conditions-review/conditions-review-c5-....csv
        → data-lab/conditions/review_draft.csv (memo에 검증 결과, 확인할 줄은 memo 맨 앞 [확인])
    (초안을 라벨 기준으로 고치고 labeler를 채운 뒤 맞는 줄만 reviewed_rules.csv에 붙인다)
    python data-lab/conditions/review_tool.py check
    python data-lab/rule_labels/export_label_rules.py
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "rule_labels"))
from check_labels import check_row, check_service, read_csv  # noqa: E402

DEFAULT_DRAFT = HERE / "review_draft.csv"
DEFAULT_REVIEWED = HERE / "reviewed_rules.csv"
COLUMNS = ["service_id", "labeled_on", "group", "field", "operator", "values", "source_quote", "question",
           "labeler", "memo"]
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
# 답 저장 API(PUT /api/me/answers)의 key 형식과 같다 (AnswerService.KEY)
ANSWER_KEY_MAX = 50

# 검수 파일 status → 초안 memo
STATUS_NOTE = {
    "KEPT": "",
    "LOW_CONFIDENCE": "[확인] 신뢰도 낮음 — 맞으면 그대로 두면 된다 (질문·수급·지역은 원래 0.65)",
    "DROPPED": "[확인] 자동 검증 실패 — 고치거나 UNPARSED로 바꾸거나 지운다",
    "UNPARSED": "",
    "SPECIAL": "",
    "REPEAT_ONLY": "[확인] 두 번째 결과에만 있음 — 첫 결과가 빠뜨린 조건인지 보고 넣거나 지운다",
}


def draft(review_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    out = []
    for r in review_rows:
        note = STATUS_NOTE.get(r.get("status", ""), "[확인] 알 수 없는 상태")
        detail = " / ".join(x for x in (r.get("reason", ""), f"신뢰도 {r['confidence']}" if r.get("confidence") else "",
                                        r.get("checks", "")) if x)
        out.append({
            "service_id": r["service_id"], "labeled_on": r["labeled_on"], "group": r["group"], "field": r["field"],
            "operator": r["operator"], "values": r["values"], "source_quote": r["source_quote"],
            "question": r["question"], "labeler": "",
            "memo": " ".join(x for x in (note, f"({detail})" if detail else "") if x),
        })
    return out


def check(rows: list[dict[str, str]]) -> list[str]:
    """오류 목록. 비어 있으면 통과."""
    errors = []
    if rows and set(COLUMNS) - set(rows[0]):
        return [f"칸이 없음: {', '.join(sorted(set(COLUMNS) - set(rows[0])))}"]
    by_service: dict[str, list[dict[str, str]]] = defaultdict(list)
    on_by_service: dict[str, set[str]] = defaultdict(set)
    for line_no, row in enumerate(rows, start=2):
        where = f"[{line_no}행] {row['service_id']} {row['field']}"
        # 근거 문구 자체를 원문으로 넘겨 형식·숫자만 본다
        for e in check_row(row, [row["source_quote"]] if row["source_quote"] else []):
            errors.append(f"{where}: {e}")
        if not DATE.match(row["labeled_on"]):
            errors.append(f"{where}: labeled_on은 YYYY-MM-DD")
        if not row["labeler"]:
            errors.append(f"{where}: labeler를 채운다 (검수한 사람)")
        if row["memo"].startswith("[확인]"):
            errors.append(f"{where}: memo의 [확인]을 처리하고 지운다")
        if row["field"].startswith("ANSWER:") and len(row["field"]) - len("ANSWER:") > ANSWER_KEY_MAX:
            errors.append(f"{where}: ANSWER key는 {ANSWER_KEY_MAX}자까지")
        by_service[row["service_id"]].append(row)
        on_by_service[row["service_id"]].add(row["labeled_on"])
    for sid, rs in by_service.items():
        errors += [f"[공고] {sid}: {e}" for e in check_service(rs)]
        if len(on_by_service[sid]) > 1:
            errors.append(f"[공고] {sid}: labeled_on이 줄마다 다름 (같은 원문으로 검수해야 한다)")
    return errors


def write(rows: list[dict[str, str]], out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)


def main() -> int:
    p = argparse.ArgumentParser(description="LLM 조건 검수 초안 만들기·검수 파일 검사")
    sub = p.add_subparsers(dest="cmd", required=True)
    d = sub.add_parser("draft", help="백엔드 검수 파일 → 검수 초안")
    d.add_argument("review", type=Path)
    d.add_argument("--out", type=Path, default=DEFAULT_DRAFT)
    c = sub.add_parser("check", help="reviewed_rules.csv 형식 검사")
    c.add_argument("--reviewed", type=Path, default=DEFAULT_REVIEWED)
    args = p.parse_args()
    if args.cmd == "draft":
        rows = draft(read_csv(args.review))
        write(rows, args.out)
        todo = sum(r["memo"].startswith("[확인]") for r in rows)
        print(f"{len(rows)}줄, 공고 {len({r['service_id'] for r in rows})}건 → {args.out} (확인할 줄 {todo}개)")
        return 0
    rows = read_csv(args.reviewed)
    errors = check(rows)
    for e in errors:
        print(e)
    print(f"\n{len(rows)}줄, 공고 {len({r['service_id'] for r in rows})}건, 오류 {len(errors)}개")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
