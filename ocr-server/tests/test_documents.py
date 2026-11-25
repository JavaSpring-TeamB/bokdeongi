from datetime import date

from app.documents import Result, check, find_issue_date, personal_document

TODAY = date(2026, 11, 16)

# 가짜 서류 OCR 결과 (실제 개인정보 아님)
등본 = [
    "주민등록표",
    "(등 본)",
    "세대주 성명 김복덩 세대구성 사유 및 일자 전입 2025-03-02",
    "현주소 서울특별시 광진구 가상로 1",
    "번호 세대주와의 관계 성명 주민등록번호",
    "1 본인 김복덩 910101-2******",
    "2026년 11월 10일",
    "서울특별시 광진구 가상동장",
]

재학 = [
    "재 학 증 명 서",
    "성명 김복덩",
    "소속 가상대학교 인공지능학과",
    "학년 4학년",
    "위 사람은 본교에 재학 중임을 증명합니다.",
    "발급일자 2026. 11. 1.",
]


def test_등본_통과():
    c = check(등본, expected_type="RESIDENT_REGISTER", max_age_days=30, today=TODAY)
    assert c.result == Result.PASS
    assert c.issue_date == date(2026, 11, 10)


def test_발급일_라벨이_있으면_그_날짜():
    assert find_issue_date(재학) == date(2026, 11, 1)


def test_기한이_지나면_요건_미달():
    c = check(재학, expected_type="ENROLLMENT", max_age_days=7, today=TODAY)
    assert c.result == Result.INVALID


def test_다른_서류면_종류_불일치():
    c = check(재학, expected_type="RESIDENT_REGISTER", today=TODAY)
    assert c.result == Result.TYPE_MISMATCH
    assert c.detected_type == "ENROLLMENT"


def test_필수_항목이_없으면_부족():
    lines = ["재학증명서", "성명 김복덩", "위 사람은 본교 학생임을 증명함", "발급일 2026-11-01"]
    c = check(lines, expected_type="ENROLLMENT", today=TODAY)
    assert c.result == Result.MISSING
    assert "학과" in c.missing_fields


def test_글자가_거의_없으면_재촬영():
    assert check(["주민", "등"], today=TODAY).result == Result.UNREADABLE


def test_미래_발급일은_요건_미달():
    lines = 재학[:-1] + ["발급일자 2027. 1. 1."]
    assert check(lines, today=TODAY).result == Result.INVALID


def test_번역기는_개인_서류를_알아본다():
    assert personal_document(등본) == "주민등록등본"
    assert personal_document(재학) == "재학증명서"
    assert personal_document(["긴급복지 지원 결정 통지서", "지원 금액 월 1,833,000원"]) is None
