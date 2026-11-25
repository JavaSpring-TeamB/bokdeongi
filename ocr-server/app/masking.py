"""OCR 텍스트에서 개인정보를 가린다. (ASST-03, EASY-02)

- 주민등록번호·외국인등록번호: 앞 6자리 + 뒷자리 첫 숫자만 남긴다  900101-1******
- 휴대폰·일반 전화: 가운데 자리를 가린다                          010-****-5678
- 계좌번호: 마지막 4자리만 남긴다                                 ***-****-1234
- 상세주소(동·층·호): 숫자를 가린다. 도로명·건물번호까지는 남긴다   가상로 12, ***호

마스킹 뒤에도 주민번호 모양이 남아 있으면 MaskingError를 던진다.
이때는 텍스트를 밖으로 내보내지 않는다(LLM에도 보내지 않음).
"""
import re

# OCR이 "900101 - 1234567", "900101–1234567"처럼 읽는 경우까지
_DASH = r"\s*[-–—~]\s*"
# 앞에 숫자가 더 붙어 읽혀도("0900505-1234567") 하이픈 앞 6자리부터 잡는다. 생년월일·성별 자리는 OCR이 잘못 읽을 수 있어 따지지 않는다
RRN = re.compile(r"(\d{6})" + _DASH + r"(\d)\s*\d(?:\s*\d){5}(?!\d)")
RRN_NO_DASH = re.compile(r"(?<!\d)(\d{2}[01]\d[0-3]\d)([1-8])(\d{6})(?!\d)")

_OPT_DASH = r"(?:" + _DASH + r")?"
MOBILE = re.compile(r"(?<!\d)(01[016789])" + _OPT_DASH + r"(\d{3,4})" + _OPT_DASH + r"(\d{4})(?!\d)")
LANDLINE = re.compile(r"(?<!\d)(0[2-6]\d?)" + _DASH + r"(\d{3,4})" + _DASH + r"(\d{4})(?!\d)")

# 계좌: 숫자 묶음 3~4개, 숫자 합계 10~16자리. 날짜(2026-11-16)는 합계가 8자리라 걸리지 않는다
ACCOUNT = re.compile(r"(?<![\d*])\d{2,6}(?:-\d{2,7}){2,3}(?![\d*])")

# 상세주소: "101동 1203호", "3층 301호", "101호". 앞이 한글·숫자면 안 본다("광진1동", "제3호"), "2호선"도 안 본다
ADDRESS_DETAIL = re.compile(r"(?<![가-힣\d])(?:\d{1,4}\s*동\s*)?(?:\d{1,3}\s*층\s*)?\d{1,5}\s*호(?!선)")

# 마스킹 뒤에 남으면 안 되는 모양
_RESIDUE = re.compile(r"\d{6}" + _DASH + r"\d{7}")


class MaskingError(Exception):
    """마스킹 후에도 주민번호가 남아 있다."""


def _mask_account(m: re.Match) -> str:
    raw = m.group(0)
    digits = sum(ch.isdigit() for ch in raw)
    if not 10 <= digits <= 16:
        return raw
    parts = raw.split("-")
    return "-".join(["*" * len(p) for p in parts[:-1]] + ["*" * max(0, len(parts[-1]) - 4) + parts[-1][-4:]])


def mask(text: str) -> str:
    """개인정보를 가린 텍스트를 돌려준다. 실패하면 MaskingError."""
    if not text:
        return text
    out = RRN.sub(lambda m: f"{m.group(1)}-{m.group(2)}******", text)
    out = RRN_NO_DASH.sub(lambda m: f"{m.group(1)}-{m.group(2)}******", out)
    out = MOBILE.sub(lambda m: f"{m.group(1)}-{'*' * len(m.group(2))}-{m.group(3)}", out)
    out = LANDLINE.sub(lambda m: f"{m.group(1)}-{'*' * len(m.group(2))}-{m.group(3)}", out)
    out = ACCOUNT.sub(_mask_account, out)
    out = ADDRESS_DETAIL.sub(lambda m: re.sub(r"\d", "*", m.group(0)), out)
    if _RESIDUE.search(out):
        raise MaskingError("주민등록번호 마스킹 실패")
    return out


def mask_lines(lines: list[str]) -> list[str]:
    """줄 단위로 가린다. 한 줄에 걸친 번호만 잡으므로 줄을 합친 텍스트도 한 번 더 검사한다."""
    masked = [mask(line) for line in lines]
    if _RESIDUE.search(" ".join(masked).replace(" ", "")):
        raise MaskingError("줄을 넘는 주민등록번호 마스킹 실패")
    return masked