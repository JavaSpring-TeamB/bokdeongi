"""document_types 시드 SQL을 만든다 (DATA-07, D-18).

입력: document_types.csv (사람이 고치는 서류 목록)
출력: document_types.sql (MySQL, 여러 번 실행해도 결과가 같다)

    python data-lab/documents/build_document_types.py
"""
import argparse
import csv
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
COLUMNS = ["name", "aliases", "issuer", "online_available", "issue_url", "issue_method",
           "default_validity_days", "alternatives", "ocr_supported"]
# D-18: OCR로 읽는 서류는 이 4종뿐이다
OCR_DOCS = {"주민등록등본", "건강보험료 납부확인서", "재학증명서", "소득금액증명원"}


def sql_str(value):
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def load(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    names = set()
    for r in rows:
        name = r["name"].strip()
        if not name or len(name) > 50:
            raise ValueError(f"서류 이름이 비었거나 50자를 넘음: {name!r}")
        if name in names:
            raise ValueError(f"서류 이름 중복: {name}")
        names.add(name)
        for flag in ("online_available", "ocr_supported"):
            if r[flag] not in ("Y", "N"):
                raise ValueError(f"{name}: {flag}는 Y 또는 N")
        if r["default_validity_days"] and not r["default_validity_days"].isdigit():
            raise ValueError(f"{name}: default_validity_days는 숫자(일)")
        if (r["ocr_supported"] == "Y") != (name in OCR_DOCS):
            raise ValueError(f"{name}: OCR 대상은 D-18의 4종만 Y")
        if r["issue_url"] and not r["issue_url"].startswith("https://"):
            raise ValueError(f"{name}: issue_url은 https://로 시작")
    aliases = {}
    for r in rows:
        for a in filter(None, (x.strip() for x in r["aliases"].split(";"))):
            if a in names or aliases.setdefault(a, r["name"]) != r["name"]:
                raise ValueError(f"별칭 {a!r}이 다른 서류 이름·별칭과 겹침")
    return rows


def to_sql(rows):
    lines = [
        "-- document_types 시드 (DATA-07, D-18). 자동 생성: data-lab/documents/build_document_types.py",
        "-- 원본: data-lab/documents/document_types.csv",
        f"-- {len(rows)}줄. name이 같으면 고친 값으로 덮어쓴다(id는 그대로라 benefits·users의 참조가 깨지지 않는다).",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "INSERT INTO `document_types` (" + ", ".join(f"`{c}`" for c in COLUMNS) + ") VALUES",
    ]
    values = []
    for r in rows:
        alias_list = [a.strip() for a in r["aliases"].split(";") if a.strip()]
        values.append("  (" + ", ".join([
            sql_str(r["name"].strip()),
            sql_str(json.dumps(alias_list, ensure_ascii=False)),
            sql_str(r["issuer"]),
            "TRUE" if r["online_available"] == "Y" else "FALSE",
            sql_str(r["issue_url"]),
            sql_str(r["issue_method"]),
            r["default_validity_days"] or "NULL",
            sql_str(r["alternatives"]),
            "TRUE" if r["ocr_supported"] == "Y" else "FALSE",
        ]) + ")")
    lines.append(",\n".join(values))
    lines.append("AS new ON DUPLICATE KEY UPDATE " + ", ".join(
        f"`{c}` = new.`{c}`" for c in COLUMNS if c != "name") + ";")
    lines.append("COMMIT;")
    lines.append("SELECT COUNT(*) AS document_types FROM `document_types`;")
    return "\n".join(lines) + "\n"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("csv", nargs="?", default=HERE / "document_types.csv")
    p.add_argument("-o", "--output", default=HERE / "document_types.sql")
    args = p.parse_args()
    rows = load(args.csv)
    Path(args.output).write_text(to_sql(rows), encoding="utf-8")
    print(f"{len(rows)}줄 → {args.output}")


if __name__ == "__main__":
    main()
