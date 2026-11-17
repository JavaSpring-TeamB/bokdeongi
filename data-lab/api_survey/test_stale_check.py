"""stale_check 분류 테스트. 실행 (레포 루트에서): python -m unittest discover -s data-lab/api_survey"""
import unittest
from datetime import date

from stale_check import Record, classify

TODAY = date(2026, 11, 16)


def label(period_text, period_type="직접입력", target="", modified=2026):
    return classify(Record("X", "t", period_type, period_text, target, modified), TODAY)


class Classify(unittest.TestCase):
    def test_old_period(self):
        self.assertEqual(label("2023.02.02~2023.04.28", "기간설정").label, "PAST_OLD")

    def test_this_year_passed(self):
        self.assertEqual(label("2026.04.27~2026.05.29", "기간설정").label, "PAST_THIS")
        self.assertEqual(label("상반기 : 2025. 11. 12. ~ 11. 26. 하반기 : 2026. 5. 18. ~ 6. 2.").label, "PAST_THIS")
        self.assertEqual(label("2026년 상반기").label, "PAST_THIS")

    def test_open(self):
        self.assertEqual(label("2026.10.15.~2027.4.30.").label, "OPEN")
        self.assertEqual(label("2026. 4.  ~ 2026.  11.").label, "OPEN")       # 월까지만이면 그달 말일까지
        self.assertEqual(label("2026. 7월 말~ 8월 말, 2027년 2월~3월(예정)").label, "OPEN")

    def test_recurring_and_no_date(self):
        self.assertEqual(label("매년 01-01~01-31").label, "RECURRING")
        self.assertEqual(label("상시신청", "상시신청").label, "NO_DATE")
        self.assertEqual(label("신청불필요").label, "NO_DATE")
        self.assertEqual(label("당해연도 예산 소진 시까지").label, "NO_DATE")
        self.assertEqual(label("2월 부터 예산 소진시 까지").label, "NO_DATE")   # 연도 표기가 없으면 판단 안 함

    def test_relative_period_is_not_deadline(self):
        self.assertEqual(label("급여의 사유가 발생한 날로부터 5년 이내(2012. 7. 1. 이후 ...)").label, "NO_DATE")
        self.assertEqual(label("생식세포 채취일로부터 6개월 (2025.1.1. 을 포함하여 그 이후에)").label, "NO_DATE")

    def test_month_only_range(self):
        self.assertEqual(label("2026.1. ~ 12.(※예산 상황에 따라 변경 가능)").label, "OPEN")

    def test_year_only(self):
        self.assertEqual(label("2025년 하반기").label, "PAST_OLD")

    def test_stale_target_text(self):
        v = label("상시신청", "상시신청", target="CT/영상/디자인 전공자로서 2024년 2월 졸업예정자")
        self.assertTrue(v.stale_text)
        self.assertFalse(label("상시신청", "상시신청", target="2026년 기준 만 19세 이상").stale_text)


if __name__ == "__main__":
    unittest.main()
