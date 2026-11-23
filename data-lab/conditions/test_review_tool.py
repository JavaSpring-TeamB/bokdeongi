import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from review_tool import COLUMNS, check, draft, write  # noqa: E402
from check_labels import read_csv  # noqa: E402


def review(**kw):
    row = {"service_id": "999000000022", "labeled_on": "2026-11-02", "group": "0", "field": "AGE",
           "operator": "GTE", "values": "19", "source_quote": "만 19세 이상", "question": "", "labeler": "",
           "memo": "", "status": "KEPT", "confidence": "1.00", "checks": "quote=O numbers=O code=O repeat=O",
           "reason": ""}
    row.update(kw)
    return row


def reviewed(**kw):
    row = {"service_id": "999000000022", "labeled_on": "2026-11-02", "group": "0", "field": "AGE",
           "operator": "GTE", "values": "19", "source_quote": "만 19세 이상", "question": "", "labeler": "전민정",
           "memo": ""}
    row.update(kw)
    return row


class DraftTest(unittest.TestCase):
    def test_검증_결과를_memo로_옮기고_확인할_줄을_표시한다(self):
        rows = draft([review(), review(status="DROPPED", reason="숫자 20이(가) 근거에 없음", confidence=""),
                      review(status="REPEAT_ONLY", reason="두 번째 결과에만 있음", confidence="")])
        self.assertEqual(COLUMNS, list(rows[0].keys()))
        self.assertFalse(rows[0]["memo"].startswith("[확인]"))
        self.assertTrue(rows[1]["memo"].startswith("[확인] 자동 검증 실패"))
        self.assertIn("숫자 20", rows[1]["memo"])
        self.assertTrue(rows[2]["memo"].startswith("[확인] 두 번째 결과에만"))
        self.assertEqual("", rows[0]["labeler"])


class CheckTest(unittest.TestCase):
    def test_검수한_줄은_통과(self):
        rows = [reviewed(),
                reviewed(field="ANSWER:married_within_7y", operator="EQ", values="Y", source_quote="혼인 7년 이내",
                         question="혼인한 지 7년 이내인가요?")]
        self.assertEqual([], check(rows))

    def test_labeler_없음_확인_남음_날짜_형식(self):
        errors = check([reviewed(labeler="", memo="[확인] 신뢰도 낮음", labeled_on="2026/11/02")])
        self.assertTrue(any("labeler" in e for e in errors))
        self.assertTrue(any("[확인]" in e for e in errors))
        self.assertTrue(any("labeled_on" in e for e in errors))

    def test_형식과_숫자를_본다(self):
        self.assertTrue(any("근거" in e for e in check([reviewed(values="20")])))
        self.assertTrue(any("question" in e for e in check([reviewed(field="ANSWER:x", operator="EQ", values="Y")])))
        self.assertTrue(any("50자" in e for e in check([reviewed(field="ANSWER:" + "a" * 51, operator="EQ",
                                                                  values="Y", question="질문?")])))

    def test_공고_안에서_labeled_on이_같아야(self):
        errors = check([reviewed(), reviewed(group="0", labeled_on="2026-11-03", field="GENDER", operator="EQ",
                                             values="F", source_quote="여성")])
        self.assertTrue(any("labeled_on이 줄마다" in e for e in errors))

    def test_파일로_쓰고_읽는다(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "draft.csv"
            write(draft([review()]), out)
            self.assertEqual(1, len(read_csv(out)))


if __name__ == "__main__":
    unittest.main()
