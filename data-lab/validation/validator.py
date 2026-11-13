"""LLM 출력 검증 모듈 (D-57, D-58, D-59).

LLM이 공고 원문에서 뽑은 값(판정 조건, 날짜, 금액 등)을 프로그램이 직접 검사해 점수를 매긴다.
LLM이 스스로 말하는 확신도는 쓰지 않는다.

검사 종류
- 근거 문구 (필수): LLM이 가리킨 근거 문구가 원문에 그대로 있는가. 없으면 버린다
- 숫자 (필수): 값의 숫자·날짜가 근거 문구 안에 있는가. 다르면 버린다 (AGENTS.md 원칙 7)
- 코드 (점수): 지원조건 코드로 만든 규칙과 같은가 (RULE만)
- 반복 (점수): 같은 공고를 다시 돌렸을 때 같은 값이 나오는가

Java로 옮길 때는 cases.json을 같은 기대값으로 통과해야 한다 (D-58).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum


class Purpose(str, Enum):
    """검증 용도. 용도마다 필수 검사가 다르다."""

    RULE = "RULE"          # 판정 조건 (DATA-03)
    EXTRACT = "EXTRACT"    # 날짜·금액·중복 불가 (DATA-04~06)
    EASY = "EASY"          # 쉬운 말 (EASY-01·02): 문장 속 숫자가 모두 원문에 있어야 한다


# 점수 = 검사별 비중의 합. 통과 1, 실패 0, 확인 못 함(None) 0.5
WEIGHTS = {"numbers": Decimal("0.4"), "code": Decimal("0.3"), "repeat": Decimal("0.3")}
UNCHECKED_CREDIT = Decimal("0.5")

# ---------------------------------------------------------------- 정리 (D-59)

# 글머리·장식 기호. 원문과 근거 문구 양쪽에서 똑같이 지운다
BULLETS = "○●◦•▪▫■□◆◇▶▷►※*ㅇ·ㆍ‧･∙-–—"
_TRANSLATE = str.maketrans({
    "～": "~", "〜": "~", "∼": "~",
    "‘": "'", "’": "'", "“": '"', "”": '"', "｢": "「", "｣": "」",
    **{ch: "" for ch in BULLETS},
})
_WHITESPACE = re.compile(r"\s+")


def normalize(text: str | None) -> str:
    """공백·줄바꿈·글머리 기호만 지운다. 글자는 바꾸지 않는다 (비슷한 문구 허용 없음)."""
    if not text:
        return ""
    return _WHITESPACE.sub("", str(text).translate(_TRANSLATE))


def quote_found(quote: str | None, sources: list[str | None]) -> bool:
    """근거 문구가 원문 필드 하나 안에 그대로 있는가. 여러 필드를 이어 붙여 찾지 않는다."""
    target = normalize(quote)
    if not target:
        return False
    return any(target in normalize(source) for source in sources)


# ---------------------------------------------------------------- 숫자 읽기

UNITS = {"억": Decimal("100000000"), "천만": Decimal("10000000"), "백만": Decimal("1000000"),
         "십만": Decimal("100000"), "만": Decimal("10000"), "천": Decimal("1000"), "백": Decimal("100")}

# 날짜를 먼저 떼어 낸다. "2026.04.27"을 소수 2026.04로 읽지 않기 위해서다
_YMD = re.compile(r"(?<![\d.])((?:19|20)\d{2})\s*[.\-/]\s*(\d{1,2})(?:\s*[.\-/]\s*(\d{1,2}))?(?!\d)")
_MD = re.compile(r"(?<![\d.,])(\d{1,2})(?:\.\s*(\d{1,2})\.|-(\d{1,2}))(?!\d)")
# 쉼표는 세 자리 묶음일 때만 숫자 일부로 본다 ("1,2순위"는 1과 2)
_NUMBER = re.compile(r"(\d{1,3}(?:,\d{3})+|\d+)(\.\d+)?\s*(억|천만|백만|십만|만|천|백)?")


def canon(value) -> Decimal:
    """비교·출력용 표준형. 200000.0 → 200000, 0.50 → 0.5."""
    number = Decimal(str(value).replace(",", ""))
    if number == number.to_integral_value():
        return Decimal(int(number))
    return number.normalize()


def extract_numbers(text: str | None) -> set[Decimal]:
    """글 속 숫자를 모두 읽는다.

    "20만원"→200000, "200천원"→200000, "19,250원"→19250, "1억 2천만원"→120000000(부분 값도 포함),
    "60%"→60, "2025. 11. 12."→2025·11·12, "01-31"→1·31.
    단위가 붙은 숫자는 곱한 값만 넣는다 ("20만"에서 20은 넣지 않는다).
    """
    if not text:
        return set()
    text = str(text)
    numbers: set[Decimal] = set()

    def take_date(match: re.Match) -> str:
        numbers.update(canon(g) for g in match.groups() if g)
        return " "

    text = _YMD.sub(take_date, text)
    text = _MD.sub(take_date, text)

    matches = list(_NUMBER.finditer(text))
    i = 0
    while i < len(matches):
        match = matches[i]
        value = canon(match.group(1) + (match.group(2) or ""))
        unit = match.group(3)
        if not unit:
            numbers.add(value)
            i += 1
            continue
        total = value * UNITS[unit]
        numbers.add(canon(total))
        # "1억 2천만", "1만 5천"처럼 더 작은 단위가 공백만 두고 이어지면 합친 값도 넣는다
        while i + 1 < len(matches):
            nxt = matches[i + 1]
            nxt_unit = nxt.group(3)
            if text[match.end():nxt.start()].strip() or not nxt_unit or UNITS[nxt_unit] >= UNITS[unit]:
                break
            part = canon(nxt.group(1) + (nxt.group(2) or "")) * UNITS[nxt_unit]
            numbers.add(canon(part))
            total += part
            match, unit, i = nxt, nxt_unit, i + 1
        numbers.add(canon(total))
        i += 1
    return numbers


# ---------------------------------------------------------------- 날짜 읽기

_DATE = re.compile(
    r"(?<![\d.])(?P<y1>(?:19|20)\d{2})\s*[.\-/]\s*(?P<m1>\d{1,2})(?:\s*[.\-/]\s*(?P<d1>\d{1,2}))?(?!\d)"  # 2026.04.27 / 2026. 4.
    r"|(?:(?P<y2>(?:19|20)\d{2})\s*년\s*)?(?P<m2>\d{1,2})\s*월(?:\s*(?P<d2>\d{1,2})\s*일)?"          # 2026년 4월 27일 / 8월
    r"|(?<![\d.,])(?P<m3>\d{1,2})(?:\.\s*(?P<d3>\d{1,2})\.|-(?P<d4>\d{1,2}))(?!\d)"                 # 11. 26. / 01-31
)


@dataclass(frozen=True)
class DateMention:
    """원문 속 날짜 하나. year는 앞 날짜에서 이어받은 값일 수 있다 (explicit=False)."""

    year: int | None
    month: int
    day: int | None
    explicit: bool


def extract_dates(text: str | None) -> list[DateMention]:
    """글 속 날짜를 순서대로 읽는다. 연도가 없으면 앞에 나온 연도를 이어받고, 월이 줄면 다음 해로 넘긴다.

    "2025. 11. 12. ~ 11. 26." → 2025-11-12, 2025-11-26
    "2026.12.1~1.31."         → 2026-12-01, 2027-01-31
    "매년 01-01~01-31"        → 연도 없음
    """
    mentions: list[DateMention] = []
    year: int | None = None
    prev_month: int | None = None
    for match in _DATE.finditer(str(text or "")):
        g = match.groupdict()
        y = g["y1"] or g["y2"]
        month = int(g["m1"] or g["m2"] or g["m3"])
        day_raw = g["d1"] or g["d2"] or g["d3"] or g["d4"]
        day = int(day_raw) if day_raw else None
        if not 1 <= month <= 12 or (day is not None and not 1 <= day <= 31):
            continue
        if y:
            year, explicit = int(y), True
        else:
            explicit = False
            if year is not None and prev_month is not None and month < prev_month:
                year += 1
        mentions.append(DateMention(year, month, day, explicit))
        prev_month = month
    return mentions


def parse_label_date(value: str) -> tuple[int | None, int, int | None]:
    """'2026-03-02' → (2026, 3, 2), '2026-03' → (2026, 3, None)."""
    parts = [int(p) for p in re.split(r"\D+", value) if p]
    if parts and parts[0] >= 1900:
        year, rest = parts[0], parts[1:]
    else:
        year, rest = None, parts
    return year, rest[0], (rest[1] if len(rest) > 1 else None)


# ---------------------------------------------------------------- 코드와 모순 여부 (RULE)

NUMERIC_OPERATORS = {"LTE", "GTE", "BETWEEN"}
INF = Decimal("Infinity")


@dataclass(frozen=True)
class Spec:
    """판정 조건 하나 (benefit_rules의 field·operator·value와 같은 모양)."""

    field: str
    operator: str
    values: tuple[str, ...]

    @classmethod
    def of(cls, field_: str, operator: str, *values) -> "Spec":
        return cls(field_, operator, tuple(str(v) for v in values))

    def key(self) -> tuple:
        """반복 비교용. IN은 순서를 무시하고 숫자는 표준형으로 맞춘다."""
        values = [_canon_text(v) for v in self.values]
        if self.operator == "IN":
            values = sorted(set(values))
        return self.field, self.operator, tuple(values)


def _canon_text(value: str) -> str:
    try:
        return str(canon(value))
    except Exception:  # 숫자가 아니면 그대로
        return value.strip()


def _interval(spec: Spec) -> tuple[Decimal, Decimal] | None:
    """숫자 조건을 허용 구간으로. 나이·소득%·인원은 0 미만이 없으므로 하한은 0."""
    try:
        numbers = [canon(v) for v in spec.values]
    except Exception:
        return None
    if spec.operator == "LTE" and len(numbers) == 1:
        return Decimal(0), numbers[0]
    if spec.operator == "GTE" and len(numbers) == 1:
        return numbers[0], INF
    if spec.operator == "BETWEEN" and len(numbers) == 2:
        return min(numbers), max(numbers)
    if spec.operator == "EQ" and len(numbers) == 1:
        return numbers[0], numbers[0]
    return None


def consistent(llm: Spec, code: Spec) -> bool:
    """LLM 조건이 코드 조건과 모순되지 않는가.

    LLM 조건이 허용하는 범위가 코드 조건 범위 안에 있으면 모순 아님 (원문이 코드보다 좁을 수는 있다).
    - 숫자: 'INCOME_PCT LTE 60'은 코드 'INCOME_PCT BETWEEN 0,75'(JA0201+0202) 안이라 모순 아님.
            'LTE 100'은 범위를 벗어나 모순
    - 목록: 'HOUSEHOLD_TYPE IN 한부모'는 코드 'IN 한부모,다자녀' 안이라 모순 아님
    - 비교할 수 없는 모양(숫자 대 목록)은 모순으로 본다 (정확도 우선)
    """
    if llm.field != code.field:
        raise ValueError(f"다른 필드끼리 비교: {llm.field} / {code.field}")
    llm_range, code_range = _interval(llm), _interval(code)
    numeric = llm.operator in NUMERIC_OPERATORS or code.operator in NUMERIC_OPERATORS
    if llm_range and code_range:
        return code_range[0] <= llm_range[0] and llm_range[1] <= code_range[1]
    if numeric:
        return False
    llm_values = {_canon_text(v) for v in llm.values}
    code_values = {_canon_text(v) for v in code.values}
    return bool(llm_values) and llm_values <= code_values


# ---------------------------------------------------------------- 입력·결과


@dataclass
class Item:
    """검증할 LLM 출력 하나.

    numbers: 근거 문구 안에 있어야 하는 숫자 (나이 [19, 34], 금액 [200000]). 계산해서 만든 값(연간 환산)은 넣지 않는다
    dates:   근거 문구 안에 있어야 하는 날짜 ('2026-03-02' 또는 '2026-03')
    rule:    RULE의 조건 (코드 모순·반복 비교에 씀)
    value:   EXTRACT의 값 (반복 비교에 씀, 예: "200000")
    text:    EASY에서 검사할 문장 전체
    """

    field: str
    source_quote: str | None = None
    numbers: list = field(default_factory=list)
    dates: list[str] = field(default_factory=list)
    rule: Spec | None = None
    value: str | None = None
    text: str | None = None

    def key(self):
        return self.rule.key() if self.rule else self.value


@dataclass
class Validation:
    checks: dict[str, bool | None]
    score: Decimal
    dropped: bool
    reason: str | None = None
    notes: list[str] = field(default_factory=list)


def _numbers_check(item: Item, quote: str) -> tuple[bool | None, list[str]]:
    """값의 숫자·날짜가 근거 문구에 있는가. 확인할 숫자가 없으면 None."""
    notes: list[str] = []
    if not item.numbers and not item.dates:
        return None, notes
    found = extract_numbers(quote)
    for raw in item.numbers:
        number = canon(raw)
        if number not in found:
            notes.append(f"숫자 {number}이(가) 근거에 없음")
            return False, notes
    mentions = extract_dates(quote)
    for date in item.dates:
        year, month, day = parse_label_date(date)
        # 월·일이 같은 날짜가 근거에 있어야 한다. 값이 월까지만이면 월만 본다
        same_day = [m for m in mentions if m.month == month and (day is None or m.day == day)]
        if not same_day:
            notes.append(f"날짜 {date}의 월·일이 근거에 없음")
            return False, notes
        if year is None or any(m.year == year for m in same_day):
            continue
        if all(m.year is None for m in same_day):
            notes.append(f"날짜 {date}의 연도는 근거에 없어 추정값")  # DATA-04 연도 추정 규칙
            continue
        notes.append(f"날짜 {date}의 연도가 근거와 다름")
        return False, notes
    return True, notes


def _score(checks: dict[str, bool | None]) -> Decimal:
    total = Decimal(0)
    for name, weight in WEIGHTS.items():
        ok = checks[name]
        total += weight * (UNCHECKED_CREDIT if ok is None else Decimal(int(ok)))
    return total.quantize(Decimal("0.01"))


def validate(item: Item, purpose: Purpose, sources: list[str | None],
             code_rule: Spec | None = None, repeat: Item | None = None) -> Validation:
    """LLM 출력 하나를 검증한다.

    sources:   공고 원문 필드들 (지원대상, 선정기준, 지원내용, 신청기한 ...)
    code_rule: 같은 필드를 지원조건 코드로 만든 조건 (DATA-02). 코드가 없는 필드면 None (RULE만 씀)
    repeat:    같은 공고를 다시 돌려 나온 같은 필드의 출력. 두 번째 실행에 그 필드가 없으면
               Item(field)처럼 빈 값을 넘긴다(불일치). 안 돌렸으면 None (D-60: RULE은 항상 2회)
    """
    checks: dict[str, bool | None] = {"quote": None, "numbers": None, "code": None, "repeat": None}

    if purpose is Purpose.EASY:
        source_numbers: set[Decimal] = set()
        for source in sources:
            source_numbers |= extract_numbers(source)
        missing = sorted(n for n in extract_numbers(item.text) if n not in source_numbers)
        checks["numbers"] = not missing
        if missing:
            return Validation(checks, Decimal("0.00"), True,
                              "원문에 없는 숫자: " + ", ".join(str(n) for n in missing))
        return Validation(checks, Decimal("1.00"), False)

    checks["quote"] = quote_found(item.source_quote, sources)
    if not checks["quote"]:
        return Validation(checks, Decimal("0.00"), True, "근거 문구가 원문에 없음")

    checks["numbers"], notes = _numbers_check(item, item.source_quote or "")
    if checks["numbers"] is False:
        return Validation(checks, Decimal("0.00"), True, notes[-1], notes)

    if purpose is Purpose.RULE and code_rule is not None:
        if item.rule is None:
            checks["code"] = False
            notes.append("조건 없이 코드와 비교할 수 없음")
        else:
            checks["code"] = consistent(item.rule, code_rule)
    if repeat is not None:
        checks["repeat"] = item.key() is not None and repeat.key() == item.key()
    return Validation(checks, _score(checks), False, None, notes)
