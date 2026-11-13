"""region_check 테스트. 실행: python -m unittest discover -s data-lab/api_survey"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import region_check  # noqa: E402


class FindSidoTest(unittest.TestCase):
    def test_full_short_none(self):
        self.assertEqual(region_check.find_sido("부산광역시 부산진구"), ("부산광역시", "full"))
        self.assertEqual(region_check.find_sido("부산 부산진구"), ("부산광역시", "short"))
        self.assertEqual(region_check.find_sido("강원도 춘천시"), ("강원특별자치도", "short"))
        self.assertEqual(region_check.find_sido("부산진구"), (None, "none"))
        # 경기도 광주시를 광주광역시로 읽으면 안 된다
        self.assertEqual(region_check.find_sido("광주시"), (None, "none"))

    def test_sigungu_part(self):
        self.assertEqual(region_check.sigungu_part("부산광역시 부산진구", "부산광역시"), "부산진구")
        self.assertEqual(region_check.sigungu_part("경기도 수원시 장안구", "경기도"), "수원시")
        self.assertEqual(region_check.sigungu_part("중구", None), "중구")
        self.assertIsNone(region_check.sigungu_part("국민연금공단", None))

    def test_merged_province(self):
        # 2026-07-01 전라남도·광주광역시 통합
        self.assertEqual(region_check.find_sido("전남광주통합특별시 영암군"), ("전남광주통합특별시", "full"))
        self.assertEqual(region_check.find_sido("전라남도 순천시"), ("전남광주통합특별시", "short"))
        self.assertEqual(region_check.find_sido("광주광역시 북구"), ("전남광주통합특별시", "short"))


class ReportTest(unittest.TestCase):
    def test_report_detects_duplicated_names(self):
        rows = [
            {"서비스ID": "1", "소관기관명": "부산광역시 중구", "소관기관코드": "3250000", "소관기관유형": "시군구"},
            {"서비스ID": "2", "소관기관명": "대구광역시 중구", "소관기관코드": "3410000", "소관기관유형": "시군구"},
            {"서비스ID": "3", "소관기관명": "부산광역시", "소관기관코드": "6260000", "소관기관유형": "광역시도"},
            {"서비스ID": "4", "소관기관명": "보건복지부", "소관기관코드": "1352000", "소관기관유형": "중앙행정기관"},
        ]
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "serviceList"
            folder.mkdir(parents=True)
            body = {"page": 1, "perPage": 10, "totalCount": 4, "data": rows}
            (folder / "page_0001.json").write_text(json.dumps(body, ensure_ascii=False), encoding="utf-8")
            report = region_check.build_report(Path(tmp))
        self.assertIn("| 시군구 | 2 | 2 | 2 | 0 | 0 |", report)
        self.assertIn("시·도가 달라도 이름이 같은 시·군·구: 1개 중구(2곳)", report)
        self.assertIn("이름 하나에 코드 여러 개: 0개 / 코드 하나에 이름 여러 개: 0개", report)
        self.assertIn("7자리(4)", report)
        self.assertIn("| 중앙행정기관 | 1 | 1 | 0 | 0 | 0 |", report)


class ResolveRegionTest(unittest.TestCase):
    rows = [
        {"소관기관명": "서울특별시 용산구", "소관기관코드": "3020000", "소관기관유형": "시군구"},
        {"소관기관명": "용산구시설관리공단", "소관기관코드": "3020000", "소관기관유형": "지방공기업"},
        {"소관기관명": "부산광역시", "소관기관코드": "6260000", "소관기관유형": "광역시도"},
        {"소관기관명": "부산신용보증재단", "소관기관코드": "6260000", "소관기관유형": "지방출자_출연기관"},
        {"소관기관명": "보건복지부", "소관기관코드": "1352000", "소관기관유형": "중앙행정기관"},
        {"소관기관명": "어딘가재단", "소관기관코드": "9999999", "소관기관유형": "지방출자_출연기관"},
    ]

    def test_code_table_and_resolution(self):
        table = region_check.code_region_table(self.rows)
        self.assertEqual(table["3020000"], ("서울특별시", "용산구"))
        self.assertEqual(table["6260000"], ("부산광역시", None))
        self.assertEqual(region_check.resolve_region(self.rows[1], table), ("서울특별시 용산구", "code"))
        self.assertEqual(region_check.resolve_region(self.rows[3], table), ("부산광역시", "code"))
        self.assertEqual(region_check.resolve_region(self.rows[4], table), ("전국", "nationwide"))
        self.assertEqual(region_check.resolve_region(self.rows[5], table), ("", "unknown"))


if __name__ == "__main__":
    unittest.main()
