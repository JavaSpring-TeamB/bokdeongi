import csv
import io
import tempfile
import unittest
from pathlib import Path

import build_document_types as b

HERE = Path(__file__).resolve().parent


class BuildTest(unittest.TestCase):
    def test_실제_목록이_검사를_통과하고_SQL이_된다(self):
        rows = b.load(HERE / "document_types.csv")
        sql = b.to_sql(rows)
        self.assertIn("ON DUPLICATE KEY UPDATE", sql)
        self.assertEqual(sql.count("\n  ("), len(rows))
        ocr = {r["name"] for r in rows if r["ocr_supported"] == "Y"}
        self.assertEqual(ocr, b.OCR_DOCS)

    def test_작은따옴표와_별칭_JSON(self):
        self.assertEqual(b.sql_str("A'B"), "'A''B'")
        self.assertEqual(b.sql_str(""), "NULL")

    def _write(self, rows):
        f = tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False, encoding="utf-8", newline="")
        w = csv.DictWriter(f, fieldnames=b.COLUMNS + ["mentions"])
        w.writeheader()
        w.writerows(rows)
        f.close()
        return f.name

    def test_별칭이_겹치면_멈춘다(self):
        base = dict(issuer="", online_available="N", issue_url="", issue_method="",
                    default_validity_days="", alternatives="", ocr_supported="N", mentions="")
        path = self._write([dict(base, name="가", aliases="겹침"), dict(base, name="나", aliases="겹침")])
        with self.assertRaises(ValueError):
            b.load(path)


if __name__ == "__main__":
    unittest.main()
