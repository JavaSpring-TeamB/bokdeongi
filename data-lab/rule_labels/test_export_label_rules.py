import csv
import tempfile
import unittest
from pathlib import Path

from export_label_rules import OUT_COLUMNS, export, write


class ExportLabelRulesTest(unittest.TestCase):
    LABELS = [
        {"service_id": "161300000099", "group": "0", "field": "AGE", "operator": "BETWEEN", "values": "19;34",
         "source_quote": "만 19~34세 이하", "question": "", "labeler": "전민정", "memo": "메모"},
        {"service_id": "161300000099", "group": "0", "field": "ANSWER:public_rental", "operator": "EQ", "values": "N",
         "source_quote": "공공임대주택 거주", "question": "공공임대주택에 살고 있나요?", "labeler": "전민정", "memo": ""},
    ]
    SAMPLES = [{"service_id": "161300000099", "modified_at": "2026-05-08"}]

    def test_수정일을_붙이고_labeler_memo는_뺀다(self):
        rows = export(self.LABELS, self.SAMPLES)
        self.assertEqual("2026-05-08", rows[0]["labeled_on"])
        self.assertNotIn("memo", rows[0])
        self.assertEqual("공공임대주택에 살고 있나요?", rows[1]["question"])

    def test_표본에_없는_서비스는_멈춘다(self):
        with self.assertRaises(SystemExit):
            export(self.LABELS, [])

    def test_파일로_쓴다(self):
        with tempfile.TemporaryDirectory() as d:
            out = Path(d) / "x" / "label-rules.csv"
            write(export(self.LABELS, self.SAMPLES), out)
            with out.open(encoding="utf-8") as f:
                rows = list(csv.DictReader(f))
            self.assertEqual(OUT_COLUMNS, list(rows[0].keys()))
            self.assertEqual(2, len(rows))

    def test_레포의_라벨이_모두_내보내진다(self):
        here = Path(__file__).resolve().parent
        labels = here / "rule_labels.csv"
        samples = here.parent / "gold_set" / "samples.csv"
        if not labels.exists() or not samples.exists():
            self.skipTest("라벨·표본 파일 없음")
        with labels.open(encoding="utf-8-sig") as f:
            n = len(list(csv.DictReader(f)))
        with samples.open(encoding="utf-8-sig") as f:
            s = list(csv.DictReader(f))
        with labels.open(encoding="utf-8-sig") as f:
            self.assertEqual(n, len(export(list(csv.DictReader(f)), s)))


if __name__ == "__main__":
    unittest.main()
