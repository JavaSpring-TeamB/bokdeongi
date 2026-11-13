"""판정 조건 라벨(rule_labels.csv) 검사.

형식(필드·연산자·값)과 근거(문구가 원문에 있는지, 숫자가 근거 안에 있는지)를 본다.
근거 검사는 LLM 출력과 같은 검증 모듈(data-lab/validation)을 쓴다: 사람 라벨도 같은 기준을 통과해야
나중에 LLM 출력과 공정하게 비교할 수 있다.

실행 (레포 루트에서):
    python data-lab/rule_labels/check_labels.py
    python data-lab/rule_labels/check_labels.py --labels 다른.csv --samples 다른/samples.csv
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "validation"))
from validator import Item, Purpose, canon, validate  # noqa: E402

DEFAULT_LABELS = HERE / "rule_labels.csv"
DEFAULT_SAMPLES = HERE.parent / "gold_set" / "samples.csv"
COLUMNS = ["service_id", "group", "field", "operator", "values", "source_quote", "question", "labeler", "memo"]
SOURCE_FIELDS = ("title", "period_text", "target_text", "criteria_text", "content_text",
                 "apply_method_text", "required_docs_text", "official_docs_text", "self_docs_text")

NUMERIC = {"AGE", "INCOME_PCT", "HOUSEHOLD_SIZE", "CHILDREN_COUNT", "YOUNGEST_CHILD_AGE"}
CODES = {
    "GENDER": {"M", "F"},
    "HOUSEHOLD_TYPE": {"JA0403", "JA0404", "JA0411", "JA0413", "JA0414"},
    "STATUS": {"JA0313", "JA0314", "JA0315", "JA0316", "JA0317", "JA0318", "JA0319", "JA0320",
               "JA0326", "JA0327", "SELF_EMPLOYED"},
    "SENSITIVE": {"JA0301", "JA0302", "JA0303", "JA0328", "JA0329", "JA0330", "JA0401", "JA0402"},
    "HOUSING": {"OWN", "JEONSE", "MONTHLY", "ETC"},
    "HAS_HOUSE": {"true", "false"},
    "MARITAL": {"SINGLE", "MARRIED", "ETC"},
    "WELFARE": {"BASIC", "NEAR_POOR"},
}
FREE_TEXT = {"REGION"}
NO_VALUE = {"UNPARSED", "NONE"}
OPERATORS = {"EQ", "IN", "LTE", "GTE", "BETWEEN"}
ANSWER = re.compile(r"^ANSWER:[a-z][a-z0-9_]*$")


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return [{k: (v or "").strip() for k, v in row.items()} for row in csv.DictReader(f)]


def check_row(row: dict[str, str], sources: list[str] | None) -> list[str]:
    """한 줄의 오류 목록. 비어 있으면 통과."""
    errors: list[str] = []
    field, op = row["field"], row["operator"]
    values = [v.strip() for v in row["values"].split(";") if v.strip()]

    if sources is None:
        return ["표본에 없는 service_id"]
    if not row["group"].isdigit():
        errors.append("group은 0 이상 정수")

    if field in NO_VALUE:
        if op or values:
            errors.append(f"{field}는 operator·values를 비운다")
        if field == "UNPARSED" and not row["source_quote"]:
            errors.append("UNPARSED도 근거 문구가 필요")
        return errors + (_quote_errors(row, sources) if row["source_quote"] else [])

    is_answer = bool(ANSWER.match(field))
    if not is_answer and field not in NUMERIC | set(CODES) | FREE_TEXT:
        errors.append(f"모르는 field: {field}" + (" (ANSWER key는 영문 소문자_밑줄)" if field.startswith("ANSWER") else ""))
        return errors
    if op not in OPERATORS:
        errors.append(f"모르는 operator: {op}")
        return errors

    expected_count = {"EQ": 1, "LTE": 1, "GTE": 1, "BETWEEN": 2}.get(op)
    if expected_count and len(values) != expected_count:
        errors.append(f"{op}는 값 {expected_count}개 (지금 {len(values)}개)")
    if op == "IN" and not values:
        errors.append("IN은 값이 1개 이상")

    if is_answer:
        if op != "EQ" or values not in (["Y"], ["N"]):
            errors.append("ANSWER는 EQ Y 또는 EQ N")
        if not row["question"]:
            errors.append("ANSWER는 question 필수")
    elif field in NUMERIC:
        try:
            numbers = [canon(v) for v in values]
        except Exception:
            errors.append(f"{field} 값은 숫자")
            numbers = []
        if op == "BETWEEN" and len(numbers) == 2 and numbers[0] > numbers[1]:
            errors.append("BETWEEN은 작은값;큰값 순서")
        if op == "IN":
            errors.append(f"{field}는 IN 대신 BETWEEN·LTE·GTE·EQ")
    elif field in CODES:
        unknown = [v for v in values if v not in CODES[field]]
        if unknown:
            errors.append(f"{field}에 없는 값: {', '.join(unknown)}")
        if op not in ("EQ", "IN"):
            errors.append(f"{field}는 EQ·IN만")
    if row["question"] and not is_answer:
        errors.append("question은 ANSWER에만 적는다")

    if not row["source_quote"]:
        errors.append("source_quote 필수")
    else:
        errors += _quote_errors(row, sources, values if field in NUMERIC else [])
    return errors


def _quote_errors(row: dict[str, str], sources: list[str], numbers: list[str] = ()) -> list[str]:
    item = Item(row["field"], row["source_quote"], numbers=list(numbers))
    result = validate(item, Purpose.RULE, sources)
    return [f"근거 검사 실패: {result.reason}"] if result.dropped else []


def check_service(rows: list[dict[str, str]]) -> list[str]:
    """공고 단위 규칙: NONE은 혼자, '또는' 묶음은 2개 이상."""
    errors = []
    fields = [r["field"] for r in rows]
    if "NONE" in fields and len(rows) > 1:
        errors.append("NONE은 다른 조건과 함께 쓰지 않는다")
    alt_groups = {r["group"] for r in rows if r["group"].isdigit() and int(r["group"]) > 0}
    if len(alt_groups) == 1:
        errors.append("'또는' 묶음이 1개뿐: 0번으로 합친다")
    return errors


def run(labels_path: Path, samples_path: Path) -> int:
    samples = {row["service_id"]: row for row in read_csv(samples_path)}
    labels = read_csv(labels_path)
    if labels and set(COLUMNS) - set(labels[0]):
        print(f"칸이 없음: {', '.join(sorted(set(COLUMNS) - set(labels[0])))}")
        return 1

    by_service: dict[str, list[dict[str, str]]] = defaultdict(list)
    error_count = 0
    for line_no, row in enumerate(labels, start=2):
        sample = samples.get(row["service_id"])
        sources = [sample.get(name) for name in SOURCE_FIELDS] if sample else None
        for error in check_row(row, sources):
            error_count += 1
            print(f"[{line_no}행] {row['service_id']} {row['field']}: {error}")
        by_service[row["service_id"]].append(row)
    for service_id, rows in by_service.items():
        for error in check_service(rows):
            error_count += 1
            print(f"[공고] {service_id}: {error}")

    missing = [sid for sid in samples if sid not in by_service]
    print(f"\n조건 {len(labels)}줄, 공고 {len(by_service)}/{len(samples)}건 라벨, 오류 {error_count}개")
    if missing:
        print("아직 라벨 없는 공고: " + ", ".join(missing))
    return 1 if error_count or missing else 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--samples", type=Path, default=DEFAULT_SAMPLES)
    args = parser.parse_args()
    sys.exit(run(args.labels, args.samples))


if __name__ == "__main__":
    main()
