"""라벨 검사 테스트. 실행 (레포 루트에서): python -m unittest discover -s data-lab/rule_labels"""
import csv
import io
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from check_labels import COLUMNS, check_row, check_service, run

SOURCES = ["ㅇ (대상) 청년(만 19~34세 이하*)으로 부모님과 별도 거주하는 무주택자",
           "○ 만 15세 이상 35세 미만의 미취업자 ○ 기초생활수급자, 차상위계층"]


def row(**kw):
    base = {c: "" for c in COLUMNS}
    base.update(group="0", labeler="민정")
    base.update(kw)
    return base


class CheckRow(unittest.TestCase):
    def ok(self, **kw):
        self.assertEqual(check_row(row(**kw), SOURCES), [])

    def bad(self, expected, **kw):
        errors = check_row(row(**kw), SOURCES)
        self.assertTrue(any(expected in e for e in errors), errors)

    def test_valid_rows(self):
        self.ok(field="AGE", operator="BETWEEN", values="19;34", source_quote="만 19~34세 이하")
        self.ok(field="AGE", operator="BETWEEN", values="15;34", source_quote="만 15세 이상 35세 미만")
        self.ok(field="HAS_HOUSE", operator="EQ", values="false", source_quote="무주택자")
        self.ok(field="WELFARE", operator="IN", values="BASIC;NEAR_POOR", source_quote="기초생활수급자, 차상위계층")
        self.ok(field="ANSWER:live_apart_from_parents", operator="EQ", values="Y",
                source_quote="부모님과 별도 거주하는", question="부모님과 따로 살고 있나요?")
        self.ok(field="UNPARSED", source_quote="미취업자")
        self.ok(field="NONE")

    def test_quote_must_be_in_source(self):
        self.bad("근거 문구가 원문에 없음", field="HAS_HOUSE", operator="EQ", values="false", source_quote="무주택 세대주")

    def test_number_must_be_in_quote(self):
        self.bad("숫자 39", field="AGE", operator="BETWEEN", values="19;39", source_quote="만 19~34세 이하")

    def test_format_errors(self):
        self.bad("BETWEEN는 값 2개", field="AGE", operator="BETWEEN", values="19", source_quote="만 19~34세 이하")
        self.bad("작은값;큰값", field="AGE", operator="BETWEEN", values="34;19", source_quote="만 19~34세 이하")
        self.bad("없는 값", field="WELFARE", operator="IN", values="LOW", source_quote="기초생활수급자")
        self.bad("모르는 field", field="AGE_RANGE", operator="EQ", values="1", source_quote="만 19~34세")
        self.bad("영문 소문자", field="ANSWER:부모별거", operator="EQ", values="Y", source_quote="부모님과", question="?")
        self.bad("question 필수", field="ANSWER:apart", operator="EQ", values="Y", source_quote="부모님과 별도 거주")
        self.bad("EQ Y 또는 EQ N", field="ANSWER:apart", operator="EQ", values="예", source_quote="부모님과", question="?")
        self.bad("source_quote 필수", field="HAS_HOUSE", operator="EQ", values="false")
        self.bad("operator·values를 비운다", field="NONE", operator="EQ")
        self.bad("group은", group="a", field="NONE")
        self.bad("question은 ANSWER에만", field="HAS_HOUSE", operator="EQ", values="false",
                 source_quote="무주택자", question="집이 없나요?")

    def test_unknown_service(self):
        self.assertEqual(check_row(row(field="NONE"), None), ["표본에 없는 service_id"])


class CheckService(unittest.TestCase):
    def test_none_alone(self):
        self.assertTrue(check_service([row(field="NONE"), row(field="AGE")]))

    def test_single_alternative_group(self):
        self.assertTrue(check_service([row(group="1", field="AGE")]))
        self.assertEqual(check_service([row(group="1", field="AGE"), row(group="2", field="AGE")]), [])


class Run(unittest.TestCase):
    def test_reports_missing_services(self):
        with tempfile.TemporaryDirectory() as tmp:
            samples, labels = Path(tmp) / "samples.csv", Path(tmp) / "labels.csv"
            with samples.open("w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, ["service_id", "target_text"])
                w.writeheader()
                w.writerows([{"service_id": "A", "target_text": SOURCES[0]}, {"service_id": "B", "target_text": "x"}])
            with labels.open("w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, COLUMNS)
                w.writeheader()
                w.writerow(row(service_id="A", field="HAS_HOUSE", operator="EQ", values="false", source_quote="무주택자"))
            out = io.StringIO()
            with redirect_stdout(out):
                code = run(labels, samples)
            self.assertEqual(code, 1)
            self.assertIn("아직 라벨 없는 공고: B", out.getvalue())


if __name__ == "__main__":
    unittest.main()
