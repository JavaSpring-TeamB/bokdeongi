"""검증 모듈 테스트. cases.json(공용 벡터) + Python 전용 경계값.

실행 (레포 루트에서): python -m unittest discover -s data-lab/validation
"""
import json
import unittest
from decimal import Decimal
from pathlib import Path

from validator import (Item, Purpose, Spec, canon, consistent, extract_dates, extract_numbers,
                       normalize, quote_found, validate)

CASES = json.loads((Path(__file__).parent / "cases.json").read_text(encoding="utf-8"))


def to_spec(data):
    return Spec.of(data["field"], data["operator"], *data["values"]) if data else None


def to_item(data):
    if data is None:
        return None
    data = dict(data)
    data["rule"] = to_spec(data.get("rule"))
    return Item(**data)


class SharedCases(unittest.TestCase):
    """Java 이식본도 같은 기대값으로 통과해야 하는 케이스."""

    def test_numbers(self):
        for case in CASES["numbers"]:
            with self.subTest(text=case["text"]):
                self.assertEqual(extract_numbers(case["text"]), {canon(n) for n in case["expected"]})

    def test_dates(self):
        for case in CASES["dates"]:
            with self.subTest(text=case["text"]):
                got = [[m.year, m.month, m.day, m.explicit] for m in extract_dates(case["text"])]
                self.assertEqual(got, case["expected"])

    def test_consistency(self):
        for case in CASES["consistency"]:
            with self.subTest(llm=case["llm"], code=case["code"]):
                llm, code = Spec.of(*case["llm"]), Spec.of(*case["code"])
                self.assertEqual(consistent(llm, code), case["expected"])

    def test_quote(self):
        for case in CASES["quote"]:
            with self.subTest(quote=case["quote"]):
                self.assertEqual(quote_found(case["quote"], case["sources"]), case["expected"])

    def test_validate(self):
        for case in CASES["validate"]:
            with self.subTest(name=case["name"]):
                result = validate(to_item(case["item"]), Purpose(case["purpose"]), case["sources"],
                                  to_spec(case.get("code_rule")), to_item(case.get("repeat")),
                                  case.get("base_year"))
                expected = case["expected"]
                self.assertEqual(result.dropped, expected["dropped"])
                self.assertEqual(result.score, Decimal(expected["score"]))
                self.assertEqual(result.checks, expected["checks"])
                if result.dropped:
                    self.assertTrue(result.reason)


class Normalize(unittest.TestCase):
    def test_removes_only_spacing_and_bullets(self):
        self.assertEqual(normalize("ㅇ (대상) 만 19～34세\n  - 무주택자"), "(대상)만19~34세무주택자")

    def test_none_and_blank(self):
        self.assertEqual(normalize(None), "")
        self.assertEqual(normalize("  \n "), "")

    def test_keeps_letters(self):
        # 비슷한 글자로 바꿔 주지 않는다: 오탈자가 있으면 근거 불일치로 버린다
        self.assertFalse(quote_found("무주택 세대주", ["무주택세대원"]))


class Boundaries(unittest.TestCase):
    def test_consistency_rejects_different_fields(self):
        with self.assertRaises(ValueError):
            consistent(Spec.of("AGE", "LTE", "34"), Spec.of("INCOME_PCT", "LTE", "60"))

    def test_rule_item_without_rule_fails_code_check(self):
        item = Item("AGE", "만 19~34세", numbers=["19", "34"])
        result = validate(item, Purpose.RULE, ["만 19~34세"], Spec.of("AGE", "BETWEEN", 19, 34))
        self.assertFalse(result.checks["code"])

    def test_bullet_hyphen_does_not_create_numbers(self):
        self.assertEqual(extract_numbers("- 소득 기준 없음"), set())

    def test_month_day_without_trailing_dot_is_decimal(self):
        # "3.5%"는 날짜가 아니라 소수
        self.assertEqual(extract_numbers("3.5%"), {Decimal("3.5")})
        self.assertEqual(extract_dates("3.5%"), [])

    def test_month_only_label_matches_day_mention(self):
        item = Item("apply_start", "2026.04.27~2026.05.29", dates=["2026-04"])
        self.assertFalse(validate(item, Purpose.EXTRACT, ["2026.04.27~2026.05.29"]).dropped)

    def test_day_label_needs_day_in_quote(self):
        # 원문은 월까지만인데 일을 지어내면 버린다
        item = Item("apply_start", "2026. 4. ~ 2026. 11.", dates=["2026-04-01"])
        self.assertTrue(validate(item, Purpose.EXTRACT, ["2026. 4. ~ 2026. 11."]).dropped)

    def test_year_inferred_note(self):
        item = Item("apply_start", "매년 01-01~01-31", dates=["2027-01-01"])
        result = validate(item, Purpose.EXTRACT, ["매년 01-01~01-31"])
        self.assertTrue(any("추정" in note for note in result.notes))

    def test_quote_required_even_without_numbers(self):
        item = Item("exclusion_text", None)
        self.assertTrue(validate(item, Purpose.EXTRACT, ["아무 원문"]).dropped)

    def test_numbers_checked_against_quote_not_whole_source(self):
        # 같은 숫자가 원문 다른 곳에 있어도 근거 문구 안에 없으면 버린다
        item = Item("AGE", "청년", numbers=["34"])
        self.assertTrue(validate(item, Purpose.RULE, ["만 19~34세 이하 청년"]).dropped)


if __name__ == "__main__":
    unittest.main()
