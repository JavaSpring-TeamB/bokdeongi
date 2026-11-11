"""실측 스크립트 테스트. 실행: python -m unittest discover -s data-lab/api_survey"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analyze  # noqa: E402
import collect  # noqa: E402
import common  # noqa: E402


class ClassifyPeriodTest(unittest.TestCase):
    def test_full_date(self):
        self.assertEqual(analyze.classify_period("2026.03.02 ~ 2026.03.31"), "날짜(연도 포함)")
        self.assertEqual(analyze.classify_period("2026년 3월 2일부터"), "날짜(연도 포함)")

    def test_date_without_year(self):
        self.assertEqual(analyze.classify_period("매년 3.2 ~ 3.31"), "날짜(연도 없음)")
        self.assertEqual(analyze.classify_period("3월 2일 ~ 3월 31일"), "날짜(연도 없음)")

    def test_relative(self):
        self.assertEqual(analyze.classify_period("출생일로부터 60일 이내"), "상대 기간")

    def test_always_and_budget_and_notice(self):
        self.assertEqual(analyze.classify_period("상시신청"), "상시")
        self.assertEqual(analyze.classify_period("예산 소진 시까지"), "예산 소진·선착순")
        self.assertEqual(analyze.classify_period("사업공고 참조"), "공고·문의 참조")

    def test_empty_and_other(self):
        self.assertEqual(analyze.classify_period(None), "비어 있음")
        self.assertEqual(analyze.classify_period("  "), "비어 있음")
        self.assertEqual(analyze.classify_period("해당 없음"), "비어 있음")
        self.assertEqual(analyze.classify_period("주택도시기금 규정에 따름"), "기타")

    def test_no_application_and_varies_and_yearly(self):
        self.assertEqual(analyze.classify_period("신청불필요"), "신청 불필요")
        self.assertEqual(analyze.classify_period("별도의 신청절차가 없음"), "신청 불필요")
        self.assertEqual(analyze.classify_period("접수기관 별 상이"), "기관별 상이")
        self.assertEqual(analyze.classify_period("매년 1~2월"), "매년 반복(월 단위)")
        self.assertEqual(analyze.classify_period("연초"), "매년 반복(월 단위)")
        self.assertEqual(analyze.classify_period("공고일로부터 2주"), "상대 기간")

    def test_full_date_wins_over_always(self):
        self.assertEqual(analyze.classify_period("상시 (단, 2026.12.31까지)"), "날짜(연도 포함)")


class HelpersTest(unittest.TestCase):
    def test_is_filled(self):
        for empty in (None, "", "  ", "null", "-"):
            self.assertFalse(analyze.is_filled(empty))
        self.assertTrue(analyze.is_filled("Y"))
        self.assertTrue(analyze.is_filled(0))

    def test_parse_datetime_formats(self):
        self.assertEqual(analyze.parse_datetime("20261110093000"), datetime(2026, 11, 10, 9, 30))
        self.assertEqual(analyze.parse_datetime("2026-11-10 09:30:00"), datetime(2026, 11, 10, 9, 30))
        self.assertEqual(analyze.parse_datetime("2026.11.10"), datetime(2026, 11, 10))
        self.assertIsNone(analyze.parse_datetime("미상"))

    def test_ja_stats_counts_y_and_no_condition(self):
        records = [
            {"서비스ID": "1", "JA0101": "Y", "JA0102": None},
            {"서비스ID": "2", "JA0101": None, "JA0102": None},
            {"서비스ID": "3", "JA0101": "y", "JA0110": "19"},
        ]
        stats, no_condition = analyze.ja_stats(records)
        self.assertEqual([code for code, _ in stats], ["JA0101", "JA0102", "JA0110"])
        self.assertEqual(no_condition, 1)
        ja0110 = dict(stats)["JA0110"]
        self.assertEqual(ja0110["19"], 1)

    def test_empty_texts_include_not_applicable(self):
        self.assertFalse(analyze.is_filled("해당없음"))
        self.assertFalse(analyze.is_filled(" 해당 없음 "))

    def test_split_multi_and_personal(self):
        self.assertEqual(analyze.split_multi("현금//현물"), ["현금", "현물"])
        # 실제 API 원문은 '||'로 구분한다
        self.assertEqual(analyze.split_multi("개인||가구||법인/시설/단체"), ["개인", "가구", "법인/시설/단체"])
        self.assertTrue(analyze.is_personal({"사용자구분": "개인||법인/시설/단체"}))
        self.assertFalse(analyze.is_personal({"사용자구분": "소상공인||법인/시설/단체"}))
        self.assertTrue(analyze.is_personal({"사용자구분": "개인//법인/시설/단체"}))
        self.assertTrue(analyze.is_personal({"사용자구분": "가구"}))
        self.assertFalse(analyze.is_personal({"사용자구분": "소상공인//법인/시설/단체"}))
        self.assertFalse(analyze.is_personal({}))

    def test_group_pattern(self):
        codes = ["JA0101", "JA0102"]
        self.assertEqual(analyze.group_pattern({"JA0101": "Y", "JA0102": "Y"}, codes), "전부 Y")
        self.assertEqual(analyze.group_pattern({"JA0101": "Y", "JA0102": None}, codes), "일부 Y")
        self.assertEqual(analyze.group_pattern({"JA0101": None, "JA0102": None}, codes), "Y 없음")

    def test_ja_groups_exclude_age_codes(self):
        groups = analyze.ja_groups([{"JA0101": "Y", "JA0102": None, "JA0110": "19", "JA0111": "34", "JA0201": "Y"}])
        self.assertEqual(groups, {"JA01": ["JA0101", "JA0102"], "JA02": ["JA0201"]})

    def test_age_pattern(self):
        self.assertEqual(analyze.age_pattern({"JA0110": "0", "JA0111": "120"}), "제한 없음(0~120 또는 빈 값)")
        self.assertEqual(analyze.age_pattern({}), "제한 없음(0~120 또는 빈 값)")
        self.assertEqual(analyze.age_pattern({"JA0110": "19", "JA0111": "34"}), "나이 제한 있음")

    def test_shorten_removes_pipes_and_newlines(self):
        self.assertEqual(analyze.shorten("a|b\nc"), "a/b c")

    def test_service_key_decodes_encoded_key(self):
        self.assertEqual(common.service_key({"DATA_GO_KR_SERVICE_KEY": "abc%2Bdef%3D%3D"}), "abc+def==")
        self.assertEqual(common.service_key({"DATA_GO_KR_SERVICE_KEY": "abc+def=="}), "abc+def==")

    def test_service_key_missing_exits(self):
        with mock.patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(SystemExit):
                common.service_key({})

    def test_load_env_reads_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, ".env").write_text("# 주석\nDATA_GO_KR_SERVICE_KEY=\"k1\"\nEMPTY=\n", encoding="utf-8")
            with mock.patch.dict("os.environ", {}, clear=True):
                env = common.load_env(Path(tmp))
        self.assertEqual(env["DATA_GO_KR_SERVICE_KEY"], "k1")


def write_page(folder: Path, page: int, rows: list[dict], total: int, per_page: int = 2) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    body = {"page": page, "perPage": per_page, "totalCount": total, "currentCount": len(rows), "data": rows}
    (folder / f"page_{page:04d}.json").write_text(json.dumps(body, ensure_ascii=False), encoding="utf-8")


class CollectTest(unittest.TestCase):
    def test_resumes_and_skips_saved_pages(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp)
            write_page(raw / "serviceList", 1, [{"서비스ID": "1"}, {"서비스ID": "2"}], total=5)
            write_page(raw / "serviceList", 2, [{"서비스ID": "3"}, {"서비스ID": "4"}], total=5)
            calls = []

            def fake_fetch(endpoint, page, per_page, key, budget):
                calls.append(page)
                return {"page": page, "perPage": per_page, "totalCount": 5, "data": [{"서비스ID": "5"}]}

            with mock.patch.object(collect, "RAW_DIR", raw), mock.patch.object(collect, "fetch_page", fake_fetch):
                collect.collect("serviceList", 2, "key", collect.CallBudget(10), delay=0)
            self.assertEqual(calls, [3])
            self.assertTrue((raw / "serviceList" / "page_0003.json").exists())

    def test_per_page_mismatch_stops(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp)
            write_page(raw / "serviceList", 1, [{"서비스ID": "1"}], total=3, per_page=100)
            with mock.patch.object(collect, "RAW_DIR", raw):
                with self.assertRaises(SystemExit):
                    collect.collect("serviceList", 500, "key", collect.CallBudget(10), delay=0)

    def test_stops_when_api_caps_page_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp)
            # 500건을 요청했는데 100건만 온 경우: 그대로 진행하면 나머지 데이터를 놓친다
            write_page(raw / "serviceList", 1, [{"서비스ID": str(i)} for i in range(100)], total=1000, per_page=500)
            with mock.patch.object(collect, "RAW_DIR", raw):
                with self.assertRaises(SystemExit):
                    collect.collect("serviceList", 500, "key", collect.CallBudget(10), delay=0)

    def test_budget_stops_run(self):
        budget = collect.CallBudget(1)
        budget.take()
        with self.assertRaises(SystemExit):
            budget.take()


class ReportTest(unittest.TestCase):
    def test_build_report_from_fake_raw(self):
        with tempfile.TemporaryDirectory() as tmp:
            raw = Path(tmp)
            write_page(raw / "serviceList", 1, [
                {"서비스ID": "1", "서비스명": "청년 월세", "지원유형": "현금", "수정일시": "20261110090000", "사용자구분": "개인"},
                {"서비스ID": "2", "서비스명": "출산 지원", "지원유형": "현금||현물", "수정일시": "20261001090000",
                 "사용자구분": "법인/시설/단체"},
            ], total=2)
            write_page(raw / "serviceDetail", 1, [
                {"서비스ID": "1", "신청기한": "상시신청", "신청기한유형": "상시", "구비서류": "○ 주민등록등본\n○ 임대차계약서"},
                {"서비스ID": "2", "신청기한": "출생일로부터 60일 이내", "신청기한유형": "기간", "구비서류": ""},
            ], total=2)
            write_page(raw / "supportConditions", 1, [
                {"서비스ID": "1", "JA0101": "Y", "JA0102": "Y", "JA0110": "19", "JA0111": "34"},
                {"서비스ID": "2", "JA0101": None, "JA0102": None, "JA0110": "0", "JA0111": "120"},
            ], total=2)
            report = analyze.build_report(raw)
        self.assertIn("| serviceList | 2 | 2 | 2 |", report)
        self.assertIn("3종 모두 있는 서비스: 2건", report)
        self.assertIn("| 상시 | 1 | 50.0% |", report)
        self.assertIn("| 상대 기간 | 1 | 50.0% |", report)
        self.assertIn("조건 미표기 → DATA-03 대상): 1건 (50.0%)", report)
        self.assertIn("최근 1일 1건", report)
        # 3-1 신청기한유형
        self.assertIn("| 상시 | 1 | 상시(1) |", report)
        # 4-1 그룹 패턴: JA01은 1건 전부 Y, 1건 Y 없음(법인 대상이라 개인·가구 0)
        self.assertIn("| JA01 | JA0101, JA0102 | 1 | 1 | 0 | 0 |", report)
        self.assertIn("| 나이 제한 있음 | 1 |", report)
        self.assertIn("19~34(1)", report)
        # 6-1 개인 대상, 6-2 지원유형 나누기
        self.assertIn("개인 또는 가구가 포함된 서비스: 1건 (50.0%)", report)
        self.assertIn("| 법인/시설/단체 | 1 |", report)
        self.assertIn("| 현금 | 2 |", report)
        self.assertIn("| 현물 | 1 |", report)

    def test_build_report_without_data_exits(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit):
                analyze.build_report(Path(tmp))


if __name__ == "__main__":
    unittest.main()
