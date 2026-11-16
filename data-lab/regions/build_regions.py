"""법정동코드 전체자료(행정표준코드관리시스템)에서 regions 시드 SQL을 만든다 (D-73).

- 현재 존재하는 시·도(코드 2자리)와 시군구(코드 5자리)만 남긴다. 읍면동·리는 버린다.
- 일반구(수원시 장안구처럼 시 아래 구)는 넣지 않는다. 혜택을 내는 기관은 시 단위라
  '수원시'만 고르면 된다.
- 시군구가 없는 세종특별자치시는 시·도 줄 하나만 둔다.

실행 (레포 루트에서):
    python data-lab/regions/build_regions.py "법정동코드 전체자료.txt" -o regions.sql
입력 파일은 탭 구분, 인코딩 CP949(내려받은 그대로) 또는 UTF-8.
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

# 화면 드롭다운 순서. 행정안전부 통계 표의 관례 순서(서울 → 광역시 → 세종 → 도 → 제주)
SIDO_ORDER = [
    "서울특별시", "부산광역시", "대구광역시", "인천광역시", "전남광주통합특별시", "대전광역시",
    "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도",
    "전북특별자치도", "경상북도", "경상남도", "제주특별자치도",
]


@dataclass(frozen=True)
class Region:
    code: str
    sido: str
    sigungu: str | None


def read_lines(path: Path) -> list[str]:
    raw = path.read_bytes()
    for encoding in ("utf-8-sig", "cp949"):
        try:
            return raw.decode(encoding).splitlines()
        except UnicodeDecodeError:
            continue
    raise ValueError(f"인코딩을 알 수 없음: {path}")


def parse(lines: list[str]) -> list[Region]:
    """법정동코드 줄에서 시·도와 시군구만 뽑는다."""
    rows = []
    for line in lines:
        parts = line.rstrip("\r").split("\t")
        if len(parts) < 3 or not parts[0].isdigit() or parts[2].strip() != "존재":
            continue
        code, name = parts[0], parts[1].split()
        if code[5:] != "00000":            # 읍면동·리
            continue
        if code[2:5] == "000":             # 시·도
            rows.append(Region(code[:2], name[0], None))
        elif len(name) == 2:               # 시군구
            rows.append(Region(code[:5], name[0], name[1]))
        elif len(name) == 1:               # 세종특별자치시: 시군구 코드지만 시·도 자체
            rows.append(Region(code[:5], name[0], None))
        # len(name) >= 3 은 일반구 → 버림
    return _drop_duplicate_sido(rows)


def _drop_duplicate_sido(rows: list[Region]) -> list[Region]:
    """같은 시·도 이름의 시·도 줄이 둘이면(세종: 36 없이 36110만 있는 경우 등) 하나만 남긴다."""
    seen, result = set(), []
    for row in rows:
        key = (row.sido, row.sigungu)
        if key in seen:
            continue
        seen.add(key)
        result.append(row)
    return result


def ordered(rows: list[Region]) -> list[Region]:
    unknown = sorted({r.sido for r in rows} - set(SIDO_ORDER))
    if unknown:
        raise ValueError(f"순서표에 없는 시·도: {unknown}. SIDO_ORDER에 추가할 것")
    missing = sorted(set(SIDO_ORDER) - {r.sido for r in rows})
    if missing:
        raise ValueError(f"자료에 없는 시·도: {missing}. 옛 자료가 아닌지 확인할 것")
    return sorted(rows, key=lambda r: (SIDO_ORDER.index(r.sido), r.sigungu is not None, r.code))


def to_sql(rows: list[Region], source: str) -> str:
    def q(value: str | None) -> str:
        return "NULL" if value is None else "'" + value.replace("'", "''") + "'"

    values = ",\n".join(
        f"  ({q(r.code)}, {q(r.sido)}, {q(r.sigungu)}, {i})" for i, r in enumerate(rows, start=1))
    return f"""-- regions 시드 (D-73). 자동 생성: data-lab/regions/build_regions.py
-- 원본: {source}
-- 시·도 {sum(r.sigungu is None for r in rows)}줄 + 시군구 {sum(r.sigungu is not None for r in rows)}줄 = {len(rows)}줄
-- 여러 번 실행해도 결과가 같다 (지우고 다시 넣음). users와 FK가 없어 지워도 회원 데이터는 그대로다.
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM `regions`;
INSERT INTO `regions` (`region_code`, `sido_name`, `sigungu_name`, `sort_order`) VALUES
{values};
COMMIT;

SELECT COUNT(*) AS `전체`, SUM(`sigungu_name` IS NULL) AS `시도`, SUM(`sigungu_name` IS NOT NULL) AS `시군구`
FROM `regions`;
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path, help="법정동코드 전체자료 txt")
    parser.add_argument("-o", "--output", type=Path, default=Path("regions.sql"))
    args = parser.parse_args()
    rows = ordered(parse(read_lines(args.source)))
    args.output.write_text(to_sql(rows, args.source.name), encoding="utf-8")
    print(f"{args.output}: {len(rows)}줄 "
          f"(시·도 {sum(r.sigungu is None for r in rows)}, 시군구 {sum(r.sigungu is not None for r in rows)})")


if __name__ == "__main__":
    sys.exit(main())
