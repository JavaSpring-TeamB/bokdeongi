"""정답셋 표본 추출 테스트. 실행: python -m unittest discover -s data-lab/gold_set"""
from __future__ import annotations
 
import sys
import unittest
from collections import Counter
from pathlib import Path
 
sys.path.insert(0, str(Path(__file__).resolve().parent))
import sample  # noqa: E402
 
PERIODS = {
    "날짜(연도 포함)": "2026.3.2.~2026.3.31.",
    "상시": "상시신청",
    "매년 반복(월 단위)": "매년 1~2월",
    "기관별 상이": "접수기관 별 상이",
    "공고·문의 참조": "공고에 따름",
    "상대 기간": "출생일로부터 60일 이내",
    "신청 불필요": "신청불필요",
    "날짜(연도 없음)": "5.1.~5.31.",
    "기타": "규정에 따름",
    "예산 소진·선착순": "예산 소진 시",
}
TYPES = ["현금", "현물", "이용권", "현금(융자)", "현금(장학금)"]
 
 
def make_records(per_class: int = 10) -> list[dict]:
    records = []
    n = 0
    for label, text in PERIODS.items():
        for i in range(per_class):
            n += 1
            records.append({"서비스ID": f"S{n:04d}", "서비스명": f"{label} 혜택 {i}", "신청기한": text,
                            "지원유형": TYPES[i % len(TYPES)], "사용자구분": "개인"})
    records.append({"서비스ID": "CORP1", "서비스명": "법인 지원", "신청기한": "상시신청",
                    "지원유형": "현금", "사용자구분": "법인/시설/단체"})
    records.append({"서비스ID": "DEMO1", "서비스명": "청년월세 한시 특별지원", "신청기한": "2026.2.26.~2026.12.31.",
                    "지원유형": "현금", "사용자구분": "개인", "소관기관유형": "중앙행정기관"})
    return records
 
 
class SampleTest(unittest.TestCase):
    def test_size_and_quotas(self):
        chosen = sample.sample(make_records(), 30, seed=1)
        self.assertEqual(len(chosen), 30)
        counts = Counter(sample.classify_period(r["신청기한"]) for r in chosen)
        self.assertEqual(counts, Counter(sample.QUOTAS))
 
    def test_must_include_demo_and_no_duplicates(self):
        chosen = sample.sample(make_records(), 30, seed=1)
        ids = [r["서비스ID"] for r in chosen]
        self.assertIn("DEMO1", ids)
        self.assertEqual(len(ids), len(set(ids)))
 
    def test_only_personal(self):
        chosen = sample.sample(make_records(), 30, seed=1)
        self.assertNotIn("CORP1", [r["서비스ID"] for r in chosen])
 
    def test_same_seed_same_result(self):
        a = [r["서비스ID"] for r in sample.sample(make_records(), 30, seed=7)]
        b = [r["서비스ID"] for r in sample.sample(make_records(), 30, seed=7)]
        self.assertEqual(a, b)
 
    def test_diverse_support_types_within_class(self):
        chosen = sample.sample(make_records(), 30, seed=3)
        dated = [r for r in chosen if sample.classify_period(r["신청기한"]) == "상시"]
        self.assertEqual(len({r["지원유형"] for r in dated}), len(dated))  # 5건이 모두 다른 유형
 
    def test_short_class_warns_but_continues(self):
        records = [r for r in make_records() if r["신청기한"] != "신청불필요"]
        chosen = sample.sample(records, 30, seed=1)
        self.assertEqual(len(chosen), 28)
 
 
if __name__ == "__main__":
    unittest.main()