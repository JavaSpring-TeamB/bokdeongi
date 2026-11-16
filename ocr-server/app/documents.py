"""서류 종류·발급일·필수 항목을 규칙으로 판정한다. (ASST-03, D-18)

OCR 대상 서류 4종: 주민등록등본, 건강보험료 납부확인서, 재학증명서, 소득금액증명원.
LLM을 부르지 않는다. 입력은 마스킹된 OCR 줄 목록이다.

판정 결과
- PASS          : 종류가 맞고 필수 항목이 다 있고 발급일이 기한 안
- MISSING       : 필수 항목 중 못 찾은 것이 있음 (부족)
- INVALID       : 발급일이 없거나 기한이 지남·미래 날짜 (요건 미달)
- TYPE_MISMATCH : 기대한 서류와 다른 서류
- UNREADABLE    : 글자를 거의 못 읽음 → 재촬영 요청
"""
import re
from dataclasses import dataclass, field
from datetime import date
from enum import Enum


class Result(str, Enum):
    PASS = "PASS"
    MISSING = "MISSING"
    INVALID = "INVALID"
    TYPE_MISMATCH = "TYPE_MISMATCH"
    UNREADABLE = "UNREADABLE"


@dataclass(frozen=True)
class DocSpec:
    code: str
    name: str
    title: re.Pattern          # 제목으로 종류를 알아본다
    fields: dict[str, re.Pattern]  # 필수 항목 이름 → 찾는 낱말


def _p(s: str) -> re.Pattern:
    return re.compile(s)


# OCR이 띄어쓰기를 제멋대로 넣으므로 비교는 공백을 지운 텍스트로 한다
SPECS: list[DocSpec] = [
    DocSpec("RESIDENT_REGISTER", "주민등록등본",
            _p(r"주민등록표\(?등본\)?|주민등록등본"),
            {"세대주": _p(r"세대주"),
             "주소": _p(r"주소|전입"),
             "세대원": _p(r"세대주와의관계|세대구성|관계")}),
    DocSpec("HEALTH_INSURANCE_PAYMENT", "건강보험료 납부확인서",
            _p(r"(건강)?보험료납부확인서"),
            {"가입자": _p(r"가입자|납부자|성명"),
             "납부기간": _p(r"납부기간|부과년월|고지년월|기간"),
             "보험료": _p(r"보험료|납부금액|금액")}),
    DocSpec("ENROLLMENT", "재학증명서",
            _p(r"재학증명서"),
            {"학교": _p(r"대학교|대학|학교"),
             "학과": _p(r"학과|전공|학부"),
             "학년": _p(r"학년|재학")}),
    DocSpec("INCOME_AMOUNT", "소득금액증명원",
            _p(r"소득금액증명(원)?"),
            {"귀속연도": _p(r"귀속연도|귀속년도|과세기간"),
             "소득금액": _p(r"소득금액|총수입금액|합계")}),
]
SPEC_BY_CODE = {s.code: s for s in SPECS}

ISSUE_LABEL = _p(r"발급일자?|발행일자?|발급일시|교부일|증명일")
DATE = re.compile(r"(20\d{2})\s*(?:년|[.\-/])\s*(\d{1,2})\s*(?:월|[.\-/])\s*(\d{1,2})\s*일?")

MIN_CHARS = 20  # 이보다 적게 읽히면 재촬영


@dataclass
class Check:
    result: Result
    detected_type: str | None = None
    issue_date: date | None = None
    found_fields: list[str] = field(default_factory=list)
    missing_fields: list[str] = field(default_factory=list)
    reason: str = ""


def _squash(s: str) -> str:
    return re.sub(r"\s+", "", s)


def detect_type(lines: list[str]) -> DocSpec | None:
    """제목으로 정한다. 제목이 두 줄로 갈려 읽히기도 해서("주민등록표" / "(등 본)") 위쪽 5줄을 이어 붙여 먼저 본다."""
    head = _squash("".join(lines[:5]))
    for flat in [head] + [_squash(line) for line in lines[5:]]:
        for spec in SPECS:
            if spec.title.search(flat):
                return spec
    return None


def _to_date(m: re.Match) -> date | None:
    try:
        return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    except ValueError:
        return None


def find_issue_date(lines: list[str]) -> date | None:
    """'발급일' 같은 낱말이 있는 줄(또는 그다음 줄)의 날짜. 없으면 문서 마지막 날짜(보통 발급일)."""
    for i, line in enumerate(lines):
        if ISSUE_LABEL.search(_squash(line)):
            for cand in lines[i:i + 2]:
                m = DATE.search(cand)
                if m and (d := _to_date(m)):
                    return d
    last = None
    for line in lines:
        for m in DATE.finditer(line):
            if d := _to_date(m):
                last = d
    return last


def check(lines: list[str], expected_type: str | None = None,
          max_age_days: int | None = None, today: date | None = None) -> Check:
    """마스킹된 OCR 줄로 서류를 판정한다."""
    today = today or date.today()
    if sum(len(_squash(l)) for l in lines) < MIN_CHARS:
        return Check(Result.UNREADABLE, reason="글자를 거의 읽지 못했어요. 밝은 곳에서 다시 찍어 주세요.")

    spec = detect_type(lines)
    if expected_type and (spec is None or spec.code != expected_type):
        want = SPEC_BY_CODE[expected_type].name if expected_type in SPEC_BY_CODE else expected_type
        got = spec.name if spec else "알 수 없는 서류"
        return Check(Result.TYPE_MISMATCH, detected_type=spec.code if spec else None,
                     reason=f"필요한 서류: {want} / 올린 서류: {got}")
    if spec is None:
        return Check(Result.UNREADABLE, reason="서류 제목을 찾지 못했어요. 첫 장이 잘 보이게 다시 찍어 주세요.")

    flat = _squash(" ".join(lines))
    found = [k for k, p in spec.fields.items() if p.search(flat)]
    missing = [k for k in spec.fields if k not in found]
    issued = find_issue_date(lines)
    base = dict(detected_type=spec.code, issue_date=issued, found_fields=found, missing_fields=missing)

    if issued is None:
        return Check(Result.INVALID, reason="발급일을 찾지 못했어요.", **base)
    if issued > today:
        return Check(Result.INVALID, reason="발급일이 오늘 이후예요. 다시 확인해 주세요.", **base)
    if max_age_days is not None and (today - issued).days > max_age_days:
        return Check(Result.INVALID, reason=f"발급일로부터 {max_age_days}일이 지났어요. 새로 발급해 주세요.", **base)
    if missing:
        return Check(Result.MISSING, reason=f"{', '.join(missing)} 항목을 찾지 못했어요.", **base)
    return Check(Result.PASS, reason="서류가 확인됐어요.", **base)