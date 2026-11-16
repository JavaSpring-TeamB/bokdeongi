"""regions 시드 생성 테스트. 실행 (레포 루트에서): python -m unittest discover -s data-lab/regions"""
import unittest

from build_regions import SIDO_ORDER, Region, ordered, parse, to_sql

LINES = [
    "법정동코드\t법정동명\t폐지여부",
    "1100000000\t서울특별시\t존재",
    "1111000000\t서울특별시 종로구\t존재",
    "1111010100\t서울특별시 종로구 청운동\t존재",        # 동 → 버림
    "1200000000\t전남광주통합특별시\t존재",
    "1221000000\t전남광주통합특별시 동구\t존재",
    "2900000000\t광주광역시\t폐지",                      # 폐지 → 버림
    "2911000000\t광주광역시 동구\t폐지",
    "2812500000\t인천광역시 제물포구\t존재",
    "2811000000\t인천광역시 중구\t폐지",
    "2800000000\t인천광역시\t존재",
    "3611000000\t세종특별자치시\t존재\r",                 # CRLF 그대로 와도 읽힘
    "4100000000\t경기도\t존재",
    "4111000000\t경기도 수원시\t존재",
    "4111100000\t경기도 수원시 장안구\t존재",            # 일반구 → 버림
]


class Parse(unittest.TestCase):
    def setUp(self):
        self.rows = parse(LINES)

    def test_keeps_only_existing_sido_and_sigungu(self):
        self.assertEqual(self.rows, [
            Region("11", "서울특별시", None),
            Region("11110", "서울특별시", "종로구"),
            Region("12", "전남광주통합특별시", None),
            Region("12210", "전남광주통합특별시", "동구"),
            Region("28125", "인천광역시", "제물포구"),
            Region("28", "인천광역시", None),
            Region("36110", "세종특별자치시", None),
            Region("41", "경기도", None),
            Region("41110", "경기도", "수원시"),
        ])

    def test_general_gu_is_dropped(self):
        self.assertNotIn("장안구", {r.sigungu for r in self.rows})

    def test_abolished_regions_are_dropped(self):
        self.assertNotIn("광주광역시", {r.sido for r in self.rows})


class Ordering(unittest.TestCase):
    def full(self):
        return [Region(str(i), name, None) for i, name in enumerate(SIDO_ORDER, start=10)]

    def test_sido_first_then_sigungu_by_code(self):
        rows = self.full() + [Region("11140", "서울특별시", "중구"), Region("11110", "서울특별시", "종로구")]
        result = ordered(rows)
        self.assertEqual([r.sigungu for r in result[:3]], [None, "종로구", "중구"])

    def test_old_file_without_merged_city_fails(self):
        rows = [r for r in self.full() if r.sido != "전남광주통합특별시"]
        with self.assertRaises(ValueError):
            ordered(rows)

    def test_unknown_sido_fails(self):
        with self.assertRaises(ValueError):
            ordered(self.full() + [Region("29", "광주광역시", None)])


class Sql(unittest.TestCase):
    def test_null_and_quote_escape(self):
        sql = to_sql([Region("11", "서울특별시", None), Region("99999", "가'나", "다")], "src.txt")
        self.assertIn("('11', '서울특별시', NULL, 1)", sql)
        self.assertIn("('99999', '가''나', '다', 2)", sql)
        self.assertIn("DELETE FROM `regions`;", sql)


if __name__ == "__main__":
    unittest.main()
